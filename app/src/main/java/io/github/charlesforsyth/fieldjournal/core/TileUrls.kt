package io.github.charlesforsyth.fieldjournal.core

/** Zero-API-key tile endpoints. USGS ArcGIS uses {z}/{y}/{x} order (row before column)! */
object TileUrls {
    const val USGS_IMAGERY_TOPO = "https://basemap.nationalmap.gov/arcgis/rest/services/USGSImageryTopo/MapServer/tile/"
    const val USGS_TOPO = "https://basemap.nationalmap.gov/arcgis/rest/services/USGSTopo/MapServer/tile/"
    const val OSM = "https://tile.openstreetmap.org/"

    fun arcgis(base: String, z: Int, x: Int, y: Int) = "$base$z/$y/$x"
    fun osm(z: Int, x: Int, y: Int) = "$OSM$z/$x/$y.png"

    val all = listOf(USGS_IMAGERY_TOPO, USGS_TOPO, OSM)
    fun requiresKey(url: String) = Regex("carto|mapbox|thunderforest|stadia|maptiler|api[_-]?key|access_token", RegexOption.IGNORE_CASE).containsMatchIn(url)
}

object Season {
    /** Simple GDD base-50°F curve proxy (normals-shaped), cumulative by month end. */
    val gddByMonth = intArrayOf(0, 0, 15, 110, 390, 830, 1420, 1960, 2370, 2560, 2600, 2600)
    val months = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")

    /** 4D scrubber filter: entries with a GPS fix whose month (0..11, America/New_York) is <= throughMonth. */
    fun visible(entries: List<Entry>, throughMonth: Int, showAll: Boolean): List<Entry> {
        val tz = java.time.ZoneId.of("America/New_York")
        return entries.filter { e ->
            e.stamp.lat != null && (showAll || java.time.Instant.ofEpochMilli(e.stamp.timeMs).atZone(tz).monthValue - 1 <= throughMonth)
        }
    }
}
