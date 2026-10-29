package io.github.charlesforsyth.fieldjournal.core

/**
 * Formatting for the instrument plate (Lens) and the journal metadata line. Pure Kotlin, unit-tested.
 * Missing values always render as "--" (offline-first: never invent a reading).
 */
data class Readouts(
    val azimuth: String, val cardinal: String, val pitch: String, val range: String, val rangeUnit: String,
    val conditions: List<Pair<String, String>>,
    /** v1.2 plate secondary row: label (with trend), value, unit, as separate columns for a clean hierarchy. */
    val plate: List<PlateCell> = emptyList(),
) {
    data class PlateCell(val label: String, val value: String, val unit: String)

    companion object {
        const val NONE = "--"

        fun of(st: EnvStamp, pitch: Double?, eyeM: Double): Readouts {
            val az = st.azimuth
            val dist = pitch?.let { GeoMath.estimateGroundDistance(eyeM, it) }
            val (range, unit) = when {
                dist == null -> NONE to ""
                dist >= 1000 -> ">1" to "km"
                dist >= 100 -> "%.0f".format(dist) to "m"
                else -> "%.1f".format(dist) to "m"
            }
            val cond = listOf(
                "Baro" to (st.baroHpa?.let { "%.1f hPa %s".format(it, st.baroTrend).trim() } ?: NONE),
                "Elev" to (st.elevRelM?.let { (if (it >= 0) "+" else "") + "%.1f m".format(it) } ?: NONE),
                "Temp" to (st.ridgeTempF?.let { "%.0f°F".format(it) } ?: NONE),
                "GDD" to (st.gdd?.let { "%.0f".format(it) } ?: NONE),
                "Light" to (st.lux?.let { "${it.toInt()} lx" } ?: NONE),
            )
            val plate = listOf(
                PlateCell(if (st.baroHpa != null && st.baroTrend.trim() in setOf("↑", "↓", "→")) "Baro ${st.baroTrend.trim()}" else "Baro",
                    st.baroHpa?.let { "%.1f".format(it) } ?: NONE, if (st.baroHpa != null) "hPa" else ""),
                PlateCell("Elev", st.elevRelM?.let { (if (it >= 0) "+" else "") + "%.1f".format(it) } ?: NONE, if (st.elevRelM != null) "m" else ""),
                PlateCell("Temp", st.ridgeTempF?.let { "%.0f".format(it) } ?: NONE, if (st.ridgeTempF != null) "°F" else ""),
                PlateCell("GDD", st.gdd?.let { "%.0f".format(it) } ?: NONE, ""),
            )
            return Readouts(
                plate = plate,
                azimuth = az?.let { "%03.0f°".format(it) } ?: NONE,
                cardinal = az?.let { GeoMath.cardinal8(it) } ?: "",
                pitch = pitch?.let { "%+.0f°".format(it) } ?: NONE,
                range = range, rangeUnit = unit, conditions = cond,
            )
        }

        /**
         * The one light wording used everywhere (masthead, settings, journal conditions): "812 lx · Deep shade".
         * Always "lx", never "lux"; the band names never reuse the display-mode names.
         */
        fun light(lux: Float?): String = lux?.let { "${it.toInt()} lx · ${ThemeLogic.canopyCategory(it)}" } ?: NONE

        /** Masthead mode tag: "Sunlight mode" (manual) or "Auto · Dusk mode". */
        fun modeTag(mode: DisplayMode, auto: Boolean): String {
            val m = when (mode) { DisplayMode.SUNLIGHT -> "Sunlight"; DisplayMode.FOREST_DUSK -> "Dusk"; DisplayMode.NIGHT_RED -> "Night red" }
            return if (auto) "Auto · $m mode" else "$m mode"
        }

        private fun coord(v: Double, pos: Char, neg: Char) = "%.5f°%c".format(kotlin.math.abs(v), if (v >= 0) pos else neg)

        /** "38.52250°N 78.43500°W" */
        fun coords(lat: Double?, lon: Double?): String? = if (lat == null || lon == null) null else "${coord(lat, 'N', 'S')} ${coord(lon, 'E', 'W')}"

        /**
         * Field data shown under a card's photo or spectrogram (never a repeat of the title):
         * audio: "8.0 s · 16 kHz mono · 38.52250°N 78.43500°W"; photo: "Brg 172° S · pitch -38° · 38.52250°N 78.43500°W".
         * Empty when there is nothing useful to say.
         */
        fun mediaLine(e: Entry, audioSeconds: Double?): String {
            val s = e.stamp; val parts = mutableListOf<String>()
            if (e.audioPath != null) {
                audioSeconds?.let { parts += "%.1f s".format(it) }
                parts += "16 kHz mono"
            } else if (e.photoPath != null) {
                s.azimuth?.let { parts += "Brg %03.0f° %s".format(it, GeoMath.cardinal8(it)) }
                s.pitch?.let { parts += "pitch %+.0f°".format(it) }
            }
            coords(s.lat, s.lon)?.let { parts += it }
            if (s.baroHpa != null) parts += "%.0f hPa".format(s.baroHpa)
            return parts.joinToString(" · ")
        }

        /** WAV duration from file size (16 kHz, 16-bit mono, 44-byte header). */
        fun wavSeconds(bytes: Long): Double? = if (bytes <= 44) null else (bytes - 44) / 32000.0

        private val autoBody = Regex("^\\s*(acoustic clip \\d+\\s?s|photo|sighting)\\s*@.*$", RegexOption.IGNORE_CASE)
        private fun norm(t: String) = t.lowercase().replace(Regex("[^a-z0-9]+"), " ").trim()

        /** True when the body only repeats the title (auto-generated captions like "Photo @ Demo Farm (open)"). */
        fun bodyRepeatsTitle(e: Entry): Boolean {
            val b = norm(excerpt(e.body)); val t = norm(e.title)
            return b.isEmpty() || b == t || autoBody.matches(e.body.trim()) || (t.length >= 8 && t.startsWith(b))
        }

        /** Barometer trend arrow to a word, for prose contexts (spec sheet). */
        fun trendWord(arrow: String) = when (arrow.trim()) { "↑" -> "rising"; "↓" -> "falling"; "→" -> "steady"; else -> "" }

        private val months = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")

        /** "27 Oct 2026 · 14:05 · Garden Beds · 38°F" (temp omitted when unknown; full conditions live in the expanded card). */
        fun metaLine(e: Entry): String {
            val d = e.dateIso // 2026-10-27T14:05:00-04:00
            val date = "${d.substring(8, 10).trimStart('0')} ${months[d.substring(5, 7).toInt() - 1]} ${d.substring(0, 4)}"
            val parts = mutableListOf(date, d.substring(11, 16), e.stamp.zone)
            e.stamp.ridgeTempF?.let { parts += "%.0f°F".format(it) }
            return parts.joinToString(" · ")
        }

        /** First ~[max] chars of the body on one line, cut on a word boundary. */
        fun excerpt(body: String, max: Int = 120): String {
            // Wiki links and hashtags are shown elsewhere on the card; keep the excerpt as plain prose.
            val flat = body.replace(Regex("\\[\\[[^\\]]*]]"), " ").replace(Regex("(?<![\\w&])#[\\w/-]+"), " ")
                .replace(Regex("\\s+"), " ").replace(Regex(" ([.,;:])"), "$1").trim()
            if (flat.length <= max) return flat
            val cut = flat.substring(0, max).substringBeforeLast(' ').trimEnd(',', ';', '.', ':')
            return "$cut…"
        }
    }
}
