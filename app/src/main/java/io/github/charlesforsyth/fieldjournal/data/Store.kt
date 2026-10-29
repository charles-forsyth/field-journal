package io.github.charlesforsyth.fieldjournal.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import io.github.charlesforsyth.fieldjournal.core.*

/** Plain SQLite store (see DECISIONS.md D3: SQLiteOpenHelper instead of Room to avoid kapt/KSP). */
class Store(ctx: Context) : SQLiteOpenHelper(ctx, "fieldjournal.db", null, 1) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE entries(id INTEGER PRIMARY KEY AUTOINCREMENT, title TEXT, body TEXT, category TEXT, tags TEXT,
            links TEXT, measures TEXT, time INTEGER, lat REAL, lon REAL, acc REAL, zone TEXT, baro REAL, trend TEXT, elev REAL,
            az REAL, pitch REAL, dist REAL, lux REAL, ridge REAL, coop REAL, freeze REAL, gust REAL, gdd REAL, soilT REAL, soilM REAL,
            photo TEXT, audio TEXT, reminder INTEGER, radius REAL, tlat REAL, tlon REAL)"""
        )
        db.execSQL("CREATE TABLE zones(id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT, property TEXT, lat REAL, lon REAL, radius REAL)")
        db.execSQL("CREATE TABLE nfc(tag TEXT PRIMARY KEY, zone TEXT)")
        Seed.entries().forEach { db.insert("entries", null, values(it)) }
    }

    override fun onUpgrade(db: SQLiteDatabase, o: Int, n: Int) {}

    private fun values(e: Entry) = ContentValues().apply {
        val s = e.stamp
        put("title", e.title); put("body", e.body); put("category", e.category.name)
        put("tags", e.tags.joinToString("\u001F")); put("links", e.vaultLinks.joinToString("\u001F")); put("measures", e.measurements.joinToString("\u001F"))
        put("time", s.timeMs); put("lat", s.lat); put("lon", s.lon); put("acc", s.gpsAccM?.toDouble()); put("zone", s.zone)
        put("baro", s.baroHpa); put("trend", s.baroTrend); put("elev", s.elevRelM); put("az", s.azimuth); put("pitch", s.pitch)
        put("dist", s.estDistM); put("lux", s.lux?.toDouble()); put("ridge", s.ridgeTempF); put("coop", s.coopTempF)
        put("freeze", s.freezeMarginF); put("gust", s.windGustMph); put("gdd", s.gdd); put("soilT", s.soilTempC); put("soilM", s.soilMoisture)
        put("photo", e.photoPath); put("audio", e.audioPath); put("reminder", if (e.geoReminder) 1 else 0); put("radius", e.reminderRadiusM)
        put("tlat", e.targetLat); put("tlon", e.targetLon)
    }

    private fun Cursor.d(c: String): Double? { val i = getColumnIndexOrThrow(c); return if (isNull(i)) null else getDouble(i) }
    private fun Cursor.s(c: String): String? { val i = getColumnIndexOrThrow(c); return if (isNull(i)) null else getString(i) }
    private fun split(s: String?) = s?.split("\u001F")?.filter { it.isNotBlank() } ?: emptyList()

    private fun read(c: Cursor) = Entry(
        id = c.getLong(c.getColumnIndexOrThrow("id")), title = c.s("title") ?: "", body = c.s("body") ?: "",
        category = runCatching { Category.valueOf(c.s("category")!!) }.getOrDefault(Category.GENERAL),
        tags = split(c.s("tags")), vaultLinks = split(c.s("links")), measurements = split(c.s("measures")),
        stamp = EnvStamp(
            c.getLong(c.getColumnIndexOrThrow("time")), c.d("lat"), c.d("lon"), c.d("acc")?.toFloat(), c.s("zone") ?: "--",
            c.d("baro"), c.s("trend") ?: "·", c.d("elev"), c.d("az"), c.d("pitch"), c.d("dist"), c.d("lux")?.toFloat(),
            c.d("ridge"), c.d("coop"), c.d("freeze"), c.d("gust"), c.d("gdd"), c.d("soilT"), c.d("soilM"),
        ),
        photoPath = c.s("photo"), audioPath = c.s("audio"), geoReminder = c.getInt(c.getColumnIndexOrThrow("reminder")) == 1,
        reminderRadiusM = c.d("radius") ?: 15.0, targetLat = c.d("tlat"), targetLon = c.d("tlon"),
    )

    fun all(): List<Entry> = readableDatabase.rawQuery("SELECT * FROM entries ORDER BY time DESC", null).use { c ->
        buildList { while (c.moveToNext()) add(read(c)) }
    }

    fun insert(e: Entry): Long = writableDatabase.insert("entries", null, values(e))
    fun update(e: Entry) { writableDatabase.update("entries", values(e), "id=?", arrayOf(e.id.toString())) }
    fun delete(id: Long) { writableDatabase.delete("entries", "id=?", arrayOf(id.toString())) }

    fun userZones(): List<PropertyZone> = readableDatabase.rawQuery("SELECT * FROM zones", null).use { c ->
        buildList { while (c.moveToNext()) add(PropertyZone(c.s("name")!!, c.s("property") ?: "Custom", c.d("lat")!!, c.d("lon")!!, c.d("radius") ?: 10.0)) }
    }

    fun addZone(z: PropertyZone) {
        writableDatabase.insert("zones", null, ContentValues().apply {
            put("name", z.name); put("property", z.property); put("lat", z.lat); put("lon", z.lon); put("radius", z.radiusM)
        })
    }

    fun deleteZone(name: String) { writableDatabase.delete("zones", "name=?", arrayOf(name)) }

    fun nfcZone(tag: String): String? = readableDatabase.rawQuery("SELECT zone FROM nfc WHERE tag=?", arrayOf(tag)).use { if (it.moveToFirst()) it.getString(0) else null }
    fun bindNfc(tag: String, zone: String) {
        writableDatabase.insertWithOnConflict("nfc", null, ContentValues().apply { put("tag", tag); put("zone", zone) }, SQLiteDatabase.CONFLICT_REPLACE)
    }
}

object Seed {
    private fun z(n: String) = Properties.builtIn.first { it.name == n }
    private const val DAY = 86_400_000L

    fun entries(now: Long = System.currentTimeMillis()): List<Entry> {
        fun mk(title: String, body: String, zone: String, daysAgo: Int, stamp: (EnvStamp) -> EnvStamp, reminder: Boolean = false): Entry {
            val zz = z(zone); val p = NoteParser.parse("$title $body")
            val base = EnvStamp(now - daysAgo * DAY, zz.lat, zz.lon, 4f, zz.name)
            return Entry(0, title, body, p.category, p.tags, p.vaultLinks, p.measurements, stamp(base), geoReminder = reminder)
        }
        return listOf(
            mk("Plot 3: Winter Wheat Variety Trial",
                "Planted experimental winter wheat block at 4 in depth, 6 in row spacing. Drenched with compost tea, light straw mulch. [[Vault: Plot_3_Log]]",
                "Field Plot 3", 2, { it.copy(baroHpa = 1016.4, baroTrend = "↑", elevRelM = -1.2, azimuth = 172.0, pitch = -38.0, lux = 31000f, ridgeTempF = 54.0, coopTempF = 57.0, freezeMarginF = 22.0, windGustMph = 9.0, gdd = 2874.0, soilTempC = 11.2) }),
            mk("Coyote Tracks Along Lower Treeline",
                "Fresh tracks heading NE along treeline, estimated 2.5 in print length. Checked boundary fence. [[Vault: Wildlife_Log]] #wildlife-survey",
                "Lower Treeline", 5, { it.copy(baroHpa = 1009.8, baroTrend = "↓", elevRelM = -14.6, azimuth = 48.0, pitch = -22.0, lux = 4200f, ridgeTempF = 41.0, coopTempF = 46.0, freezeMarginF = 9.0, windGustMph = 17.0, gdd = 2861.0) }),
            mk("Riparian Buffer Stream Gauging",
                "Stream weir flowing 3.8 gpm, water 48°F, clear. Cleared debris from upstream catch basin. [[Vault: Riparian_Buffer_Log]]",
                "Riparian Buffer", 9, { it.copy(baroHpa = 1012.1, baroTrend = "→", elevRelM = -9.1, azimuth = 265.0, pitch = -55.0, lux = 1800f, ridgeTempF = 50.0, coopTempF = 52.0, freezeMarginF = 18.0, windGustMph = 6.0, gdd = 2840.0, soilTempC = 12.0) }),
            mk("Geo-Reminder: Perimeter Fence Insulator",
                "Inspect insulator bracket on north boundary fence, post 7. Bring 2 spares.",
                "Weather Station", 1, { it.copy(baroHpa = 1014.0, baroTrend = "→", elevRelM = 1.3, azimuth = 2.0, pitch = -8.0, lux = 12000f, ridgeTempF = 47.0, coopTempF = 50.0, freezeMarginF = 15.0, windGustMph = 11.0, gdd = 2880.0) }, reminder = true),
            mk("Equipment Shed Winter Prep",
                "Equipment shed: fuel stabilization in tractor, drained implement sprayer lines, inspected perimeter seals. [[Vault: Workshop_Staging_Log]]",
                "Workshop / Staging", 12, { it.copy(baroHpa = 1018.2, baroTrend = "↑", elevRelM = 6.8, azimuth = 120.0, pitch = -5.0, lux = 22000f, ridgeTempF = 45.0, coopTempF = 49.0, freezeMarginF = 13.0, windGustMph = 14.0, gdd = 2820.0) }),
            mk("Field Station Pond & Forest Survey",
                "Surveyed pond perimeter transect, water level 6 in below overflow mark. Deer browse noted along woodland margin. [[Vault: Pond_Transect_Log]]",
                "Pond Transect", 18, { it.copy(baroHpa = 1011.5, baroTrend = "→", azimuth = 200.0, pitch = -12.0, lux = 9000f, gdd = 2790.0) }),
        )
    }
}
