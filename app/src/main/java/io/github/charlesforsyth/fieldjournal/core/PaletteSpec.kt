package io.github.charlesforsyth.fieldjournal.core

import kotlin.math.pow

/**
 * Colour specification for the three display modes, as plain ARGB ints so it can be unit-tested
 * (WCAG contrast, red-only night mode) without Android. The Compose layer maps these to Color.
 *
 * Role rules (enforced by ContrastTest):
 *  - text / heading / dim / primary / secondary / warn must reach WCAG AA 4.5:1 on both bg and card.
 *  - every entry of [uiTextPairs] (the actual foreground/background pair each small UI text sits on) must reach 4.5:1.
 *  - onPrimary must reach 4.5:1 on primary (filled primary button).
 *  - muted (lichen), brass (light mode), rule and chip are decorative: rules, ticks, marks, never body text.
 */
data class PaletteSpec(
    val mode: DisplayMode,
    val bg: Int,        // paper
    val card: Int,      // parchment
    val chip: Int,      // canvas khaki: chips, rule fills
    val rule: Int,      // hairline rules and borders
    val primary: Int,   // saddle brown
    val onPrimary: Int,
    val heading: Int,   // dark leather
    val secondary: Int, // pine green
    val muted: Int,     // lichen (decorative)
    val brass: Int,     // rare highlights, active marks
    val text: Int,      // ink
    val dim: Int,       // secondary text
    val warn: Int,      // oxide red, warnings only
    val halo: Int,      // dark outline under the reticle / map marks
) {
    // ---- Named UI text roles (v1.2). The Compose layer reads these names, so the test checks what is drawn. ----
    /** Search / field-note placeholder. */
    val placeholder get() = dim
    /** Inactive filter tabs, layer tabs, display-mode tabs. */
    val tabInactive get() = dim
    /** Inactive bottom-nav label and icon. */
    val navInactive get() = dim
    /** Journal metadata line, pin count, entry count, plate labels, month letters, units. */
    val meta get() = dim
    /** Text on a filled "ON" stamp of the two-position switch. */
    val stampOnText get() = if (mode == DisplayMode.NIGHT_RED) bg else onPrimary
    /** Fill of the "ON" stamp. */
    val stampOnFill get() = secondary
    /** Fill of the selected "OFF" half: same inked-stamp language as ON, in the quieter dim ink. */
    val stampOffFill get() = if (mode == DisplayMode.FOREST_DUSK) 0xFF56625A.toInt() else dim
    /** Text on the OFF stamp (cream on the recessed loden at dusk; paper elsewhere). */
    val stampOffText get() = if (mode == DisplayMode.FOREST_DUSK) heading else stampOnText
    /** "GDD" curve label on the season ruler. */
    val curveLabel get() = secondary

    /** Colours that are used for readable text, with the role name (for test messages). */
    val textRoles: List<Pair<String, Int>>
        get() = listOf("text" to text, "heading" to heading, "dim" to dim, "primary" to primary, "secondary" to secondary, "warn" to warn) +
            (if (mode == DisplayMode.SUNLIGHT) emptyList() else listOf("brass" to brass))

    /** Every small UI text and the surface it actually sits on (v1.2 sunlight-legibility audit). */
    val uiTextPairs: List<Triple<String, Int, Int>>
        get() = listOf(
            Triple("search placeholder on paper", placeholder, bg),
            Triple("field-note placeholder on paper", placeholder, bg),
            Triple("inactive filter tab on paper", tabInactive, bg),
            Triple("inactive map layer tab on paper", tabInactive, bg),
            Triple("inactive nav label/icon on paper", navInactive, bg),
            Triple("pin count on paper", meta, bg),
            Triple("month ticks on paper", meta, bg),
            Triple("journal metadata on paper", meta, bg),
            Triple("media field data on paper", meta, bg),
            Triple("masthead lux line on paper", meta, bg),
            Triple("masthead mode tag on paper", primary, bg),
            Triple("instrument plate label on card", meta, card),
            Triple("instrument plate value on card", heading, card),
            Triple("settings reading value on card", heading, card),
            Triple("settings unit column on card", meta, card),
            Triple("settings label on paper", text, bg),
            Triple("switch OFF/ON idle half on paper", meta, bg),
            Triple("switch ON stamp", stampOnText, stampOnFill),
            Triple("switch OFF stamp", stampOffText, stampOffFill),
            Triple("GDD curve label on paper", curveLabel, bg),
            Triple("overlay tag on viewfinder plate", heading, bg),
            Triple("map attribution on plate", meta, bg),
            Triple("ray control state on paper", meta, bg),
            Triple("ray locked bearing on paper", heading, bg),
            Triple("cluster count on stamp", heading, bg),
        )

    val all: List<Int> get() = listOf(bg, card, chip, rule, primary, onPrimary, heading, secondary, muted, brass, text, dim, warn, halo)

    companion object {
        private fun c(hex: Long) = (hex or 0xFF000000L).toInt()

        val SUNLIGHT = PaletteSpec(
            DisplayMode.SUNLIGHT,
            bg = c(0xF2EADB), card = c(0xE8DCC4), chip = c(0xCBB994), rule = c(0xCBB994),
            primary = c(0x6B4A2F), onPrimary = c(0xF2EADB), heading = c(0x3B2A1E), secondary = c(0x3E5B3F),
            // v1.2: dim darkened from #65553F (6.0:1) to #54452F (7.6:1 on paper, 6.7:1 on parchment) for direct sun.
            muted = c(0x8A9A6B), brass = c(0xA8864F), text = c(0x2A231D), dim = c(0x54452F), warn = c(0x8E3B2E),
            halo = c(0x2A231D),
        )
        /** v1.2: deep loden / pine with true brass, replacing the muddy brown-black of 1.1. */
        val FOREST_DUSK = PaletteSpec(
            DisplayMode.FOREST_DUSK,
            bg = c(0x141A15), card = c(0x1C241D), chip = c(0x26302A), rule = c(0x35413A),
            primary = c(0xCFAA72), onPrimary = c(0x141A15), heading = c(0xF2EBDA), secondary = c(0xA9BC8C),
            muted = c(0x7F9170), brass = c(0xB89758), text = c(0xEAE3D0), dim = c(0xB8B39D), warn = c(0xE59478),
            halo = c(0x0A0D0B),
        )
        /** 620 nm: every colour is red-channel only (G = B = 0). Hierarchy comes from type, not hue. */
        val NIGHT_RED = PaletteSpec(
            DisplayMode.NIGHT_RED,
            bg = c(0x000000), card = c(0x0E0000), chip = c(0x300000), rule = c(0x5A0000),
            primary = c(0xFF0000), onPrimary = c(0x000000), heading = c(0xFF0000), secondary = c(0xFF0000),
            muted = c(0x8A0000), brass = c(0xFF0000), text = c(0xFF0000), dim = c(0xF00000), warn = c(0xFF0000),
            halo = c(0x000000),
        )

        fun forMode(m: DisplayMode) = when (m) {
            DisplayMode.SUNLIGHT -> SUNLIGHT
            DisplayMode.FOREST_DUSK -> FOREST_DUSK
            DisplayMode.NIGHT_RED -> NIGHT_RED
        }

        /** WCAG 2.x relative luminance of an ARGB int (alpha ignored). */
        fun luminance(argb: Int): Double {
            fun ch(v: Int): Double { val s = v / 255.0; return if (s <= 0.03928) s / 12.92 else ((s + 0.055) / 1.055).pow(2.4) }
            return 0.2126 * ch((argb shr 16) and 0xFF) + 0.7152 * ch((argb shr 8) and 0xFF) + 0.0722 * ch(argb and 0xFF)
        }

        fun contrast(a: Int, b: Int): Double {
            val la = luminance(a); val lb = luminance(b)
            return (maxOf(la, lb) + 0.05) / (minOf(la, lb) + 0.05)
        }

        fun isRedOnly(argb: Int) = ((argb shr 8) and 0xFF) == 0 && (argb and 0xFF) == 0
    }
}
