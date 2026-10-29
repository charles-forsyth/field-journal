package io.github.charlesforsyth.fieldjournal.core

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

enum class Category(val emoji: String, val label: String, val short: String, val mark: String) {
    AGRONOMY("🌱", "Agronomy", "Garden", "G"),
    WILDLIFE("🐾", "Wildlife/Tracks", "Wildlife", "W"),
    FLEET("🔧", "Fleet/Repair", "Fleet", "F"),
    WATER("💧", "Water/Spring", "Water", "S"),
    FORAGE("🍄", "Forage", "Forage", "M"),
    GENERAL("📓", "General", "General", "N");
    val display get() = "$emoji $label"
}

/** Snapshot of hardware + estate state at entry creation. Null = unavailable ("--"). */
data class EnvStamp(
    val timeMs: Long,
    val lat: Double? = null,
    val lon: Double? = null,
    val gpsAccM: Float? = null,
    val zone: String = "NO FIX",
    val baroHpa: Double? = null,
    val baroTrend: String = "·",
    val elevRelM: Double? = null,
    val azimuth: Double? = null,
    val pitch: Double? = null,
    val estDistM: Double? = null,
    val lux: Float? = null,
    val ridgeTempF: Double? = null,
    val coopTempF: Double? = null,
    val freezeMarginF: Double? = null,
    val windGustMph: Double? = null,
    val gdd: Double? = null,
    val soilTempC: Double? = null,
    val soilMoisture: Double? = null,
) {
    fun f(v: Double?, d: Int = 1) = v?.let { "%.${d}f".format(it) } ?: "--"
    val canopy get() = ThemeLogic.canopyCategory(lux)
    fun hudLine(): String {
        val elev = elevRelM?.let { (if (it >= 0) "+" else "") + "%.1fm".format(it) } ?: "--"
        val t = ridgeTempF?.let { "%.0f°F".format(it) } ?: "--"
        return "${zone.uppercase()} | $t | GDD ${f(gdd, 0)} | ${f(baroHpa)}hPa$baroTrend | ELEV $elev | ${lux?.toInt() ?: "--"} lx"
    }
}

data class Entry(
    val id: Long = 0,
    val title: String,
    val body: String,
    val category: Category,
    val tags: List<String>,
    val vaultLinks: List<String>,
    val measurements: List<String>,
    val stamp: EnvStamp,
    val photoPath: String? = null,
    val audioPath: String? = null,
    val geoReminder: Boolean = false,
    val reminderRadiusM: Double = 15.0,
    val targetLat: Double? = null,
    val targetLon: Double? = null,
) {
    val dateIso: String get() = DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(Instant.ofEpochMilli(stamp.timeMs).atZone(ZoneId.of("America/New_York")))
}

data class ParsedNote(val category: Category, val tags: List<String>, val measurements: List<String>, val vaultLinks: List<String>)

/** Deterministic offline note parser (keyword + regex rules). AI enrichment only augments this. */
object NoteParser {
    private data class Rule(val re: Regex, val tags: List<String>, val cat: Category)

    private fun r(p: String, cat: Category, vararg tags: String) = Rule(Regex(p, RegexOption.IGNORE_CASE), tags.toList(), cat)

    private val rules = listOf(
        r("\\bgarlic\\b|chesnok", Category.AGRONOMY, "#agronomy", "#agronomy/garlic"),
        r("\\bkelp\\b", Category.AGRONOMY, "#agronomy", "#kelp-tea"),
        r("\\btomato(es)?\\b", Category.AGRONOMY, "#agronomy", "#agronomy/tomato"),
        r("\\bpeppers?\\b", Category.AGRONOMY, "#agronomy", "#agronomy/pepper"),
        r("\\bcucumbers?\\b", Category.AGRONOMY, "#agronomy", "#agronomy/cucumber"),
        r("\\bcarrots?\\b", Category.AGRONOMY, "#agronomy", "#agronomy/carrot"),
        r("\\bsage\\b", Category.AGRONOMY, "#agronomy", "#agronomy/sage"),
        r("\\b(plant(ed|ing)?|sow(n|ed)?|mulch(ed)?|compost|transplant(ed)?|harvest(ed)?|bed \\d)\\b", Category.AGRONOMY, "#agronomy"),
        r("\\bcoyotes?\\b", Category.WILDLIFE, "#wildlife", "#predator", "#predator/coyote"),
        r("\\bfox(es)?\\b", Category.WILDLIFE, "#wildlife", "#predator", "#predator/fox"),
        r("\\bbears?\\b", Category.WILDLIFE, "#wildlife", "#predator", "#predator/bear"),
        r("\\bhawks?\\b", Category.WILDLIFE, "#wildlife", "#predator", "#predator/hawk"),
        r("\\b(raccoons?|weasels?|mink)\\b", Category.WILDLIFE, "#wildlife", "#predator"),
        r("\\bdeer\\b", Category.WILDLIFE, "#wildlife", "#wildlife/deer"),
        r("\\b(tracks?|scat|prints?)\\b", Category.WILDLIFE, "#wildlife", "#tracks"),
        r("\\btractor\\b|\\butv\\b|\\btrailer\\b|\\brv\\b", Category.FLEET, "#fleet", "#trailer"),
        r("\\bram\\b|\\btruck\\b", Category.FLEET, "#fleet", "#ram"),
        r("\\b(winteriz\\w*|antifreeze|oil change|tires?|bearing|generator|repair(ed)?|fix(ed)?|insulator|fence)\\b", Category.FLEET, "#repair"),
        r("\\b(spring box|spring|creek|pond|flow|cistern|well)\\b", Category.WATER, "#water"),
        r("\\b(morels?|chanterelles?|chicken of the woods|ramps|mushrooms?|ginseng|forag\\w*)\\b", Category.FORAGE, "#forage"),
    )

    private val unit = "(?:°\\s?[FC]|degrees?|deg|in(?:ch(?:es)?)?|\"|ft|feet|'|cm|mm|m|meters?|gal(?:lons?)?|l|liters?|lpm|gpm|oz|lbs?|pounds?|psi|%|hpa|mph)"
    private val measureRe = Regex("(?<![\\w.])(\\d+(?:\\.\\d+)?)\\s?($unit)(?![A-Za-z])", RegexOption.IGNORE_CASE)
    private val vaultRe = Regex("\\[\\[\\s*(?:Vault\\s*:\\s*)?([^\\]]+?)\\s*]]", RegexOption.IGNORE_CASE)
    private val hashRe = Regex("(?<![\\w&])#([a-z0-9][a-z0-9/_-]*)", RegexOption.IGNORE_CASE)
    private val bedRe = Regex("\\b(?:bed|plot)\\s?(\\d)\\b", RegexOption.IGNORE_CASE)

    fun parse(text: String): ParsedNote {
        val tags = linkedSetOf<String>()
        val score = mutableMapOf<Category, Int>()
        for (rule in rules) {
            val n = rule.re.findAll(text).count()
            if (n > 0) { tags += rule.tags; score[rule.cat] = (score[rule.cat] ?: 0) + n }
        }
        hashRe.findAll(text).forEach { tags += "#" + it.groupValues[1].lowercase() }
        val measurements = measureRe.findAll(text).map { normaliseMeasure(it.groupValues[1], it.groupValues[2]) }.distinct().toList()
        val links = linkedSetOf<String>()
        vaultRe.findAll(text).forEach { links += "[[Vault: ${it.groupValues[1].trim()}]]" }
        bedRe.findAll(text).forEach { links += "[[Vault: Plot_${it.groupValues[1]}_Log]]" }
        // Priority on ties: wildlife > water > fleet > forage > agronomy (safety-relevant first)
        val prio = listOf(Category.WILDLIFE, Category.WATER, Category.FLEET, Category.FORAGE, Category.AGRONOMY)
        val best = score.maxOfOrNull { it.value }
        val cat = if (best == null) Category.GENERAL else prio.first { score[it] == best }
        return ParsedNote(cat, tags.toList(), measurements, links.toList())
    }

    private fun normaliseMeasure(num: String, u: String): String {
        val l = u.lowercase().replace(" ", "")
        val nu = when {
            l.startsWith("°") -> "°" + l.last().uppercaseChar()
            l.startsWith("deg") -> "°"
            l == "\"" || l.startsWith("in") -> "in"
            l == "'" || l == "feet" -> "ft"
            l.startsWith("gal") -> "gal"
            l.startsWith("lb") || l.startsWith("pound") -> "lb"
            l.startsWith("meter") -> "m"
            l.startsWith("liter") -> "L"
            l == "l" -> "L"
            l == "hpa" -> "hPa"
            else -> l
        }
        return "$num $nu".replace(" °", "°").replace(" %", "%")
    }
}

object MarkdownExport {
    private fun q(s: String) = "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\""
    private fun num(v: Double?, d: Int) = v?.let { "%.${d}f".format(java.util.Locale.US, it) } ?: "null"

    fun fileName(e: Entry): String {
        val d = e.dateIso.substring(0, 10)
        val slug = e.title.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-').take(60)
        return "$d-$slug.md"
    }

    fun toMarkdown(e: Entry): String {
        val s = e.stamp
        val sb = StringBuilder()
        sb.append("---\n")
        sb.append("title: ").append(q(e.title)).append('\n')
        sb.append("date: ").append(e.dateIso).append('\n')
        sb.append("category: ").append(q(e.category.label)).append('\n')
        sb.append("zone: ").append(q(s.zone)).append('\n')
        sb.append("lat: ").append(num(s.lat, 6)).append('\n')
        sb.append("lon: ").append(num(s.lon, 6)).append('\n')
        sb.append("azimuth: ").append(num(s.azimuth, 1)).append('\n')
        sb.append("pitch: ").append(num(s.pitch, 1)).append('\n')
        sb.append("elevation_m: ").append(num(s.elevRelM, 1)).append('\n')
        sb.append("temp_f: ").append(num(s.ridgeTempF, 1)).append('\n')
        sb.append("coop_temp_f: ").append(num(s.coopTempF, 1)).append('\n')
        sb.append("gdd: ").append(num(s.gdd, 0)).append('\n')
        sb.append("baro_hpa: ").append(num(s.baroHpa, 1)).append('\n')
        sb.append("lux: ").append(s.lux?.let { it.toInt().toString() } ?: "null").append('\n')
        sb.append("tags: [").append(e.tags.joinToString(", ") { q(it.removePrefix("#")) }).append("]\n")
        sb.append("vault_links: [").append(e.vaultLinks.joinToString(", ") { q(it) }).append("]\n")
        if (e.measurements.isNotEmpty()) sb.append("measurements: [").append(e.measurements.joinToString(", ") { q(it) }).append("]\n")
        if (e.geoReminder) sb.append("geo_reminder_radius_m: ").append(num(e.reminderRadiusM, 0)).append('\n')
        if (e.targetLat != null) sb.append("target: [").append(num(e.targetLat, 6)).append(", ").append(num(e.targetLon, 6)).append("]\n")
        sb.append("source: field-journal\n")
        sb.append("---\n\n")
        sb.append("# ").append(e.category.emoji).append(' ').append(e.title).append("\n\n")
        sb.append(e.body.trim()).append("\n\n")
        sb.append("> ").append(s.hudLine()).append("\n")
        if (e.tags.isNotEmpty()) sb.append("\n").append(e.tags.joinToString(" ")).append('\n')
        if (e.vaultLinks.isNotEmpty()) sb.append("\n").append(e.vaultLinks.joinToString(" ") { it.replace("[[Vault: ", "[[") }).append('\n')
        return sb.toString()
    }
}
