package io.github.charlesforsyth.fieldjournal.core

data class PropertyZone(
    val name: String,
    val property: String,
    val lat: Double,
    val lon: Double,
    val radiusM: Double,
    val vaultNote: String = name.replace(Regex("[^A-Za-z0-9]+"), "_").trim('_') + "_Log",
)

object Properties {
    const val DEMO_FARM = "Demo Farm"
    const val FIELD_STATION = "Field Station"
    val DEMO_CENTER = LatLon(38.5225, -78.4350)
    val STATION_CENTER = LatLon(38.5500, -78.4000)

    private fun off(c: LatLon, northM: Double, eastM: Double): LatLon {
        val a = GeoMath.destination(c.lat, c.lon, 0.0, northM)
        return GeoMath.destination(a.lat, a.lon, 90.0, eastM)
    }

    /** Built-in layout for the demo research farm and station. User zones override in Settings. */
    val builtIn: List<PropertyZone> by lazy {
        val d = DEMO_CENTER; val s = STATION_CENTER
        val plots = (1..6).map { i ->
            val p = off(d, -12.0, -15.0 + (i - 1) * 6.0)
            PropertyZone("Field Plot $i", DEMO_FARM, p.lat, p.lon, 2.8, "Plot_${i}_Log")
        }
        plots + listOf(
            off(d, 25.0, 20.0).let { PropertyZone("Weather Station", DEMO_FARM, it.lat, it.lon, 8.0, "Weather_Station_Log") },
            off(d, 90.0, -40.0).let { PropertyZone("Workshop / Staging", DEMO_FARM, it.lat, it.lon, 25.0, "Workshop_Staging_Log") },
            off(d, -110.0, 60.0).let { PropertyZone("Riparian Buffer", DEMO_FARM, it.lat, it.lon, 20.0, "Riparian_Buffer_Log") },
            off(d, -180.0, -60.0).let { PropertyZone("Lower Treeline", DEMO_FARM, it.lat, it.lon, 40.0, "Lower_Treeline_Log") },
            off(d, 10.0, -70.0).let { PropertyZone("Research Orchard", DEMO_FARM, it.lat, it.lon, 20.0, "Orchard_Log") },
            off(s, 0.0, 0.0).let { PropertyZone("Research Cabin", FIELD_STATION, it.lat, it.lon, 15.0, "Research_Cabin_Log") },
            off(s, 40.0, 50.0).let { PropertyZone("Tower Site", FIELD_STATION, it.lat, it.lon, 15.0, "Tower_Site_Log") },
            off(s, -90.0, 30.0).let { PropertyZone("Pond Transect", FIELD_STATION, it.lat, it.lon, 30.0, "Pond_Transect_Log") },
            off(s, -250.0, -200.0).let { PropertyZone("Upland Woods", FIELD_STATION, it.lat, it.lon, 260.0, "Upland_Woods_Log") },
        )
    }
}

object ZoneResolver {
    /** Picks the containing zone with the smallest radius (most specific); falls back to property within 1.5 km. */
    fun resolve(lat: Double, lon: Double, zones: List<PropertyZone> = Properties.builtIn): PropertyZone? {
        val inside = zones.filter { GeoMath.haversineM(lat, lon, it.lat, it.lon) <= it.radiusM }
        return inside.minByOrNull { it.radiusM }
    }

    fun label(lat: Double?, lon: Double?, zones: List<PropertyZone> = Properties.builtIn): String {
        if (lat == null || lon == null) return "NO FIX"
        resolve(lat, lon, zones)?.let { return it.name }
        val dDemo = GeoMath.haversineM(lat, lon, Properties.DEMO_CENTER.lat, Properties.DEMO_CENTER.lon)
        val dStation = GeoMath.haversineM(lat, lon, Properties.STATION_CENTER.lat, Properties.STATION_CENTER.lon)
        return when {
            dDemo <= 1500 -> "${Properties.DEMO_FARM} (open)"
            dStation <= 1500 -> "${Properties.FIELD_STATION} (open)"
            else -> "Off-Property"
        }
    }
}

data class GeoReminder(val id: Long, val text: String, val lat: Double, val lon: Double, val radiusM: Double = 15.0)

object GeoReminders {
    fun isTriggered(r: GeoReminder, lat: Double, lon: Double): Boolean =
        GeoMath.haversineM(lat, lon, r.lat, r.lon) <= r.radiusM

    fun triggered(list: List<GeoReminder>, lat: Double, lon: Double) = list.filter { isTriggered(it, lat, lon) }
}
