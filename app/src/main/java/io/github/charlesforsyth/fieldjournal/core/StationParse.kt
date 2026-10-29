package io.github.charlesforsyth.fieldjournal.core

import org.json.JSONObject

/** Parsed weather/sensor telemetry. Tolerant of flat or nested state shapes. */
data class StationTelemetry(
    val ridgeTempF: Double?, val coopTempF: Double?, val freezeMarginF: Double?, val windGustMph: Double?, val gdd: Double?,
)

object StationParse {
    private val keys = mapOf(
        "ridge" to listOf("ridge_temp_f", "ridgeTempF", "ridge_f", "outdoor_temp_f", "temp_f"),
        "coop" to listOf("coop_temp_f", "coopTempF", "coop_f", "inside_f"),
        "freeze" to listOf("freeze_margin_f", "freezeMarginF", "freeze_margin"),
        "gust" to listOf("wind_gust_mph", "windGustMph", "gust_mph", "wind_gust"),
        "gdd" to listOf("gdd", "gdd_cumulative", "growing_degree_days", "gddCumulative"),
    )

    private fun find(o: JSONObject, names: List<String>, depth: Int = 0): Double? {
        for (n in names) if (o.has(n) && !o.isNull(n)) o.optDouble(n).takeIf { !it.isNaN() }?.let { return it }
        if (depth > 3) return null
        val it = o.keys()
        while (it.hasNext()) { val k = it.next(); o.optJSONObject(k)?.let { c -> find(c, names, depth + 1)?.let { v -> return v } } }
        return null
    }

    fun parse(json: String): StationTelemetry? = try {
        val o = JSONObject(json)
        val ridge = find(o, keys["ridge"]!!); val coop = find(o, keys["coop"]!!)
        val freeze = find(o, keys["freeze"]!!) ?: (coop ?: ridge)?.let { it - 32.0 }
        StationTelemetry(ridge, coop, freeze, find(o, keys["gust"]!!), find(o, keys["gdd"]!!))
    } catch (_: Exception) { null }

    /** Open-Meteo hourly soil payload: picks the hour nearest `nowMs`. Returns (soilTempC, moisture m³/m³). */
    fun parseSoil(json: String, nowMs: Long): Pair<Double?, Double?>? = try {
        val h = JSONObject(json).getJSONObject("hourly")
        val times = h.getJSONArray("time")
        val nowHour = java.time.Instant.ofEpochMilli(nowMs).atZone(java.time.ZoneOffset.UTC).toLocalDateTime().withMinute(0).withSecond(0).withNano(0).toString().take(16)
        var idx = 0
        for (i in 0 until times.length()) if (times.getString(i) == nowHour) { idx = i; break }
        val t = h.optJSONArray("soil_temperature_6cm")?.optDouble(idx)?.takeIf { !it.isNaN() }
        val m = h.optJSONArray("soil_moisture_3_to_9cm")?.optDouble(idx)?.takeIf { !it.isNaN() }
        t to m
    } catch (_: Exception) { null }
}
