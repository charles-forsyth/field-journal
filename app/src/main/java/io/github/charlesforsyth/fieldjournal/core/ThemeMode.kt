package io.github.charlesforsyth.fieldjournal.core

enum class DisplayMode(val label: String) { SUNLIGHT("Sunlight"), FOREST_DUSK("Forest Dusk"), NIGHT_RED("Night 620nm Red") }
enum class DisplayPref(val label: String, val short: String) {
    AUTO("Auto Lux", "Auto"), SUNLIGHT("Sunlight", "Sunlight"), FOREST_DUSK("Forest Dusk", "Dusk"), NIGHT_RED("Night 620nm Red", "Night")
}

object ThemeLogic {
    const val SUN_LUX = 10_000f
    const val NIGHT_LUX = 10f

    fun modeForLux(lux: Float?): DisplayMode = when {
        lux == null -> DisplayMode.FOREST_DUSK
        lux >= SUN_LUX -> DisplayMode.SUNLIGHT
        lux < NIGHT_LUX -> DisplayMode.NIGHT_RED
        else -> DisplayMode.FOREST_DUSK
    }

    fun resolve(pref: DisplayPref, lux: Float?): DisplayMode = when (pref) {
        DisplayPref.AUTO -> modeForLux(lux)
        DisplayPref.SUNLIGHT -> DisplayMode.SUNLIGHT
        DisplayPref.FOREST_DUSK -> DisplayMode.FOREST_DUSK
        DisplayPref.NIGHT_RED -> DisplayMode.NIGHT_RED
    }

    /** 4x5 matrix: luminance into red only, green & blue zeroed — 620nm-safe. */
    val RED_620_MATRIX = floatArrayOf(
        0.299f, 0.587f, 0.114f, 0f, 0f,
        0f, 0f, 0f, 0f, 0f,
        0f, 0f, 0f, 0f, 0f,
        0f, 0f, 0f, 1f, 0f,
    )

    /**
     * Forest Dusk for drawn maps (topo, street): inverted luminance tinted toward deep loden, so white paper becomes
     * pine-dark (~#1E261F) and dark linework becomes soft cream (~#EAE3D0). Pure luminance mix keeps it hue-stable.
     * v1.2: was a warm leather-brown that read as muddy next to the new loden palette.
     */
    val DARK_INVERT_MATRIX = floatArrayOf(
        -0.239f, -0.470f, -0.091f, 0f, 234f,
        -0.222f, -0.435f, -0.085f, 0f, 227f,
        -0.208f, -0.407f, -0.079f, 0f, 208f,
        0f, 0f, 0f, 1f, 0f,
    )

    /**
     * Sunlight: soft warm "printed survey" filter (v1.2). 1.1 used a classic sepia blend that pushed white to
     * (255,255,234) - a sickly yellow. Now: keep 65% saturation (luminance-weighted), then scale each channel so
     * white lands on the app's cream paper (~#F2EADB) and black stays near-black. Blue is scaled only ~10% below
     * red, so it reads as cream, not yellow, and the full black-to-white range (contrast) is kept.
     */
    val SEPIA_SOFT_MATRIX: FloatArray = run {
        val sat = 0.65f; val k = 1f - sat
        val l = floatArrayOf(0.2126f, 0.7152f, 0.0722f)
        val scale = floatArrayOf(240f / 255f, 233f / 255f, 218f / 255f); val lift = floatArrayOf(4f, 3f, 2f)
        FloatArray(20).also { m ->
            for (row in 0..2) {
                for (col in 0..2) m[row * 5 + col] = ((if (row == col) sat else 0f) + k * l[col]) * scale[row]
                m[row * 5 + 4] = lift[row]
            }
            m[18] = 1f
        }
    }

    /**
     * Forest Dusk for OSM ("Survey"). OSM's land (#F2EFE9) and roads (#FFFFFF) are nearly the same luminance, so a
     * plain inversion leaves roads invisible. This one is steep (gain 2.0) around OSM's land tone, so land lands on loden
     * (#1E2A21), white roads become near-black channels and the grey road casings become light sage lines either side,
     * which is what makes roads read. Labels clip to cream-white; water and forest become mid sage.
     */
    val DUSK_SURVEY_MATRIX = floatArrayOf(
        -0.425f, -1.430f, -0.144f, 0f, 508f,
        -0.412f, -1.387f, -0.140f, 0f, 506f,
        -0.383f, -1.287f, -0.130f, 0f, 464f,
        0f, 0f, 0f, 1f, 0f,
    )

    /** Forest Dusk on imagery: dim to ~70% with a warm loden cast (no inversion: photos must stay photos). */
    val DUSK_IMAGERY_MATRIX = floatArrayOf(
        0.72f, 0.06f, 0.02f, 0f, 0f,
        0.04f, 0.70f, 0.02f, 0f, 0f,
        0.02f, 0.04f, 0.58f, 0f, 0f,
        0f, 0f, 0f, 1f, 0f,
    )

    /** Night, drawn maps: inverted luminance into red at ~60% — black paper, dim red linework. No glare. */
    val RED_620_INVERT_MATRIX = floatArrayOf(
        -0.179f, -0.352f, -0.068f, 0f, 160f,
        0f, 0f, 0f, 0f, 0f,
        0f, 0f, 0f, 0f, 0f,
        0f, 0f, 0f, 1f, 0f,
    )

    /** Night, photos / camera preview: luminance into red at 55% brightness so a bright sky doesn't flood the eye. */
    val RED_620_DIM_MATRIX = floatArrayOf(
        0.164f, 0.323f, 0.063f, 0f, 0f,
        0f, 0f, 0f, 0f, 0f,
        0f, 0f, 0f, 0f, 0f,
        0f, 0f, 0f, 1f, 0f,
    )

    /**
     * Forest Dusk for OSM street tiles. OSM draws white roads on near-white land, so any inversion turns roads darker
     * than land. Instead keep OSM's own structure (grey casings, dark labels) and dim it to a warm parchment-at-dusk tone.
     */
    val DUSK_STREET_MATRIX = floatArrayOf(
        0.50f, 0.04f, 0.01f, 0f, 0f,
        0.03f, 0.48f, 0.01f, 0f, 0f,
        0.01f, 0.03f, 0.38f, 0f, 0f,
        0f, 0f, 0f, 1f, 0f,
    )

    /** Tile filter for a display mode and layer kind. Night is always red-only and never bright. */
    fun tileMatrix(mode: DisplayMode, imagery: Boolean, street: Boolean = false): FloatArray = when (mode) {
        DisplayMode.NIGHT_RED -> if (imagery) RED_620_DIM_MATRIX else RED_620_INVERT_MATRIX
        DisplayMode.SUNLIGHT -> SEPIA_SOFT_MATRIX
        // v1.2: OSM ("Survey") uses the same pine inversion as Topo. The 1.1 dimmed-OSM read as flat khaki mud in
        // the loden palette; inverted, labels become cream on dark halos and roads read as channels between light casings.
        DisplayMode.FOREST_DUSK -> when { imagery -> DUSK_IMAGERY_MATRIX; street -> DUSK_SURVEY_MATRIX; else -> DARK_INVERT_MATRIX }
    }

    fun matrixFor(mode: DisplayMode): FloatArray? = when (mode) {
        DisplayMode.SUNLIGHT -> null
        DisplayMode.FOREST_DUSK -> DARK_INVERT_MATRIX
        DisplayMode.NIGHT_RED -> RED_620_MATRIX
    }

    /** Applies a 4x5 matrix to an ARGB pixel (for tests and software rendering). */
    fun apply(m: FloatArray, r: Int, g: Int, b: Int, a: Int = 255): IntArray {
        fun row(i: Int) = (m[i * 5] * r + m[i * 5 + 1] * g + m[i * 5 + 2] * b + m[i * 5 + 3] * a + m[i * 5 + 4]).toInt().coerceIn(0, 255)
        return intArrayOf(row(0), row(1), row(2), row(3))
    }

    /**
     * Canopy light category from lux. v1.2: band names never reuse display-mode names ("Sunlight", "Dusk", "Night"),
     * so "Sunlight mode" next to "0 lx · Dark" can't read as a contradiction. (≈ PAR bands, 1 µmol/m²/s ≈ 54 lx.)
     */
    fun canopyCategory(lux: Float?): String = when {
        lux == null -> "--"
        lux >= 50_000 -> "Full sun"
        lux >= 10_000 -> "Part sun"
        lux >= 2_000 -> "Light shade"
        lux >= 200 -> "Deep shade"
        lux >= 10 -> "Low light"
        else -> "Dark"
    }

    fun parFromLux(lux: Float?): Int? = lux?.let { (it / 54f).toInt() }
}
