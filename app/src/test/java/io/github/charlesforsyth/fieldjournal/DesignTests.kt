package io.github.charlesforsyth.fieldjournal

import io.github.charlesforsyth.fieldjournal.core.*
import io.github.charlesforsyth.fieldjournal.ui.Spectrogram
import org.junit.Assert.*
import org.junit.Test

/** v1.1 design system: WCAG contrast, palette spec, night-mode purity, tile filters, readout formatting. */
class ContrastTest {
    private val modes = DisplayMode.entries.map { PaletteSpec.forMode(it) }

    @Test fun textRolesMeetWcagAaOnBackgroundAndCards() {
        for (p in modes) for ((role, c) in p.textRoles) for ((surfName, surf) in listOf("bg" to p.bg, "card" to p.card)) {
            val r = PaletteSpec.contrast(c, surf)
            assertTrue("${p.mode} $role on $surfName = %.2f:1 (< 4.5)".format(r), r >= 4.5)
        }
    }
    @Test fun primaryButtonLabelMeetsAa() {
        for (p in modes) assertTrue("${p.mode} onPrimary/primary", PaletteSpec.contrast(p.onPrimary, p.primary) >= 4.5)
    }
    @Test fun sunlightUsesTheSpecifiedFieldGuidePalette() {
        val s = PaletteSpec.SUNLIGHT
        fun hex(i: Int) = "%06X".format(i and 0xFFFFFF)
        assertEquals("F2EADB", hex(s.bg)); assertEquals("E8DCC4", hex(s.card)); assertEquals("CBB994", hex(s.chip))
        assertEquals("6B4A2F", hex(s.primary)); assertEquals("3B2A1E", hex(s.heading)); assertEquals("3E5B3F", hex(s.secondary))
        assertEquals("8A9A6B", hex(s.muted)); assertEquals("A8864F", hex(s.brass)); assertEquals("2A231D", hex(s.text)); assertEquals("8E3B2E", hex(s.warn))
    }
    @Test fun forestDuskIsDeepPineWithBrassAndCreamText() {
        val d = PaletteSpec.FOREST_DUSK
        assertEquals(0x141A15, d.bg and 0xFFFFFF); assertEquals(0x1C241D, d.card and 0xFFFFFF); assertEquals(0xB89758, d.brass and 0xFFFFFF)
        fun ch(c: Int, s: Int) = (c shr s) and 0xFF
        // loden/pine, not brown: green is the strongest channel of bg and card (1.1 had red strongest = muddy brown).
        for (c in listOf(d.bg, d.card)) assertTrue("%06X should lean green".format(c and 0xFFFFFF), ch(c, 8) > ch(c, 16) && ch(c, 8) > ch(c, 0))
        // cream text: red >= green > blue
        assertTrue(ch(d.text, 16) >= ch(d.text, 8) && ch(d.text, 8) > ch(d.text, 0))
        assertTrue(PaletteSpec.luminance(d.bg) < 0.02); assertTrue(PaletteSpec.contrast(d.text, d.bg) > 10)
    }
    @Test fun everySmallUiTextMeetsAaOnItsActualBackground() {
        for (p in modes) for ((role, fg, bg) in p.uiTextPairs) {
            val r = PaletteSpec.contrast(fg, bg)
            assertTrue("${p.mode} $role = %.2f:1 (< 4.5)".format(r), r >= 4.5)
        }
    }
    @Test fun sunlightSecondaryTextHasSunlightMargin() {
        // v1.2: muted text roles carry >= 6.5:1 on paper in Sunlight (AA plus headroom for glare).
        val s = PaletteSpec.SUNLIGHT
        for (c in listOf(s.placeholder, s.tabInactive, s.navInactive, s.meta)) assertTrue(PaletteSpec.contrast(c, s.bg) >= 6.5)
        assertTrue(PaletteSpec.contrast(s.meta, s.card) >= 6.0)
    }
    @Test fun reticleHaloIsDarkInLitModes() {
        for (p in listOf(PaletteSpec.SUNLIGHT, PaletteSpec.FOREST_DUSK)) assertTrue(PaletteSpec.luminance(p.halo) < 0.03)
        // brass on its halo must separate (>= 3:1, graphical-object threshold)
        assertTrue(PaletteSpec.contrast(PaletteSpec.FOREST_DUSK.brass, PaletteSpec.FOREST_DUSK.halo) >= 3.0)
        assertTrue(PaletteSpec.contrast(0xFFC9A55E.toInt(), PaletteSpec.SUNLIGHT.halo) >= 3.0)
    }
    @Test fun nightIsStrictlyRedOnly() {
        PaletteSpec.NIGHT_RED.all.forEach { assertTrue("%08X not red-only".format(it), PaletteSpec.isRedOnly(it)) }
    }
    @Test fun knownContrastValues() {
        assertEquals(21.0, PaletteSpec.contrast(0xFF000000.toInt(), 0xFFFFFFFF.toInt()), 0.01)
        assertEquals(1.0, PaletteSpec.contrast(0xFF6B4A2F.toInt(), 0xFF6B4A2F.toInt()), 1e-9)
    }
}

class TileFilterTest {
    @Test fun nightTilesAreRedOnlyInEveryLayer() {
        assertNotSame(ThemeLogic.RED_620_MATRIX, ThemeLogic.tileMatrix(DisplayMode.NIGHT_RED, false))
        for (img in listOf(true, false)) {
            val px = ThemeLogic.apply(ThemeLogic.tileMatrix(DisplayMode.NIGHT_RED, img), 40, 200, 90)
            assertEquals(0, px[1]); assertEquals(0, px[2])
        }
    }
    @Test fun sunlightSepiaWarmsAndSoftensWhite() {
        val w = ThemeLogic.apply(ThemeLogic.tileMatrix(DisplayMode.SUNLIGHT, false), 255, 255, 255)
        assertTrue("red >= green >= blue (warm)", w[0] >= w[1] && w[1] >= w[2])
        assertTrue("no pure white glare", w[2] < 250)
        // v1.2: cream, not yellow. 1.1 gave (255,255,234): blue/red 0.92 at full green. Now land is close to the app's paper.
        assertTrue("white lands near paper #F2EADB: ${w.toList()}", kotlin.math.abs(w[0] - 0xF2) <= 6 && kotlin.math.abs(w[1] - 0xEA) <= 6 && kotlin.math.abs(w[2] - 0xDB) <= 6)
        assertTrue("green never above red (no yellow-green cast)", w[1] <= w[0])
        val k = ThemeLogic.apply(ThemeLogic.tileMatrix(DisplayMode.SUNLIGHT, false), 0, 0, 0)
        assertTrue("blacks stay dark: ${k.toList()}", k[0] < 12)
        // contrast between black linework and white paper is kept (>= 12:1)
        fun argb(px: IntArray) = (0xFF shl 24) or (px[0] shl 16) or (px[1] shl 8) or px[2]
        assertTrue(PaletteSpec.contrast(argb(w), argb(k)) >= 12.0)
        // USGS topo's pale yellow-green land tint (#F4F3D6-ish) must not come out greener-than-red
        val land = ThemeLogic.apply(ThemeLogic.tileMatrix(DisplayMode.SUNLIGHT, false), 0xF4, 0xF3, 0xD6)
        assertTrue("land: ${land.toList()}", land[1] <= land[0])
        val green = ThemeLogic.apply(ThemeLogic.tileMatrix(DisplayMode.SUNLIGHT, true), 30, 160, 40)
        assertTrue("forest stays greenish", green[1] > green[0])
    }
    @Test fun duskImageryIsDimmedNotInverted() {
        val m = ThemeLogic.tileMatrix(DisplayMode.FOREST_DUSK, true)
        val dark = ThemeLogic.apply(m, 10, 10, 10); val light = ThemeLogic.apply(m, 240, 240, 240)
        assertTrue(light[0] > dark[0]); assertTrue(light[0] < 240)
        assertSame(ThemeLogic.DARK_INVERT_MATRIX, ThemeLogic.tileMatrix(DisplayMode.FOREST_DUSK, false))
    }
}

class ReadoutsTest {
    private fun stamp(az: Double? = null) = EnvStamp(java.time.ZonedDateTime.parse("2026-10-07T09:05:00-04:00").toInstant().toEpochMilli(),
        zone = "Garden Beds", azimuth = az, baroHpa = 1012.44, baroTrend = "↓", ridgeTempF = 38.4, elevRelM = -2.04, lux = 812.7f)

    @Test fun instrumentPlate() {
        val r = Readouts.of(stamp(142.0), -14.0, 1.6)
        assertEquals("142°", r.azimuth); assertEquals("SE", r.cardinal); assertEquals("-14°", r.pitch)
        assertEquals("6.4", r.range); assertEquals("m", r.rangeUnit)
        assertEquals("1012.4 hPa ↓", r.conditions.toMap()["Baro"]); assertEquals("-2.0 m", r.conditions.toMap()["Elev"])
        assertEquals("38°F", r.conditions.toMap()["Temp"]); assertEquals("--", r.conditions.toMap()["GDD"]); assertEquals("812 lx", r.conditions.toMap()["Light"])
    }
    @Test fun missingReadingsAreDashesNeverInvented() {
        val r = Readouts.of(EnvStamp(0L), null, 1.6)
        assertEquals("--", r.azimuth); assertEquals("", r.cardinal); assertEquals("--", r.pitch); assertEquals("--", r.range)
        assertTrue(r.conditions.all { it.second == "--" })
        assertEquals("steady", Readouts.trendWord("→")); assertEquals("", Readouts.trendWord("·"))
        assertEquals("--", Readouts.of(EnvStamp(0L), 5.0, 1.6).range) // above horizon
        assertEquals(">1", Readouts.of(EnvStamp(0L), -0.5, 10.0).range)
        assertEquals("km", Readouts.of(EnvStamp(0L), -0.5, 10.0).rangeUnit)
        assertEquals("160", Readouts.of(EnvStamp(0L), -Math.toDegrees(Math.atan(0.01)), 1.6).range)
    }
    @Test fun metaLineAndExcerpt() {
        val e = Entry(1, "t", "b", Category.GENERAL, emptyList(), emptyList(), emptyList(), stamp())
        assertEquals("7 Oct 2026 · 09:05 · Garden Beds · 38°F", Readouts.metaLine(e))
        val bare = e.copy(stamp = EnvStamp(e.stamp.timeMs, zone = "NO FIX"))
        assertEquals("7 Oct 2026 · 09:05 · NO FIX", Readouts.metaLine(bare))
        assertEquals("short  text".replace("  ", " "), Readouts.excerpt("short \n text"))
        val long = "The garlic went in along the north edge of bed three, mulched with straw, and the soil was cool and damp at four inches."
        val ex = Readouts.excerpt(long, 60)
        assertTrue(ex.endsWith("…")); assertTrue(ex.length <= 61); assertFalse(ex.contains("  "))
        assertTrue(long.startsWith(ex.removeSuffix("…")))
    }
    @Test fun categoryShortLabelsAndMarksAreUnique() {
        assertEquals(Category.entries.size, Category.entries.map { it.short }.toSet().size)
        assertEquals(Category.entries.size, Category.entries.map { it.mark }.toSet().size)
        assertTrue(Category.entries.all { it.mark.length == 1 })
    }
}

class SpectrogramPaletteTest {
    @Test fun nightWaterfallIsRedOnly() {
        val f = Spectrogram.paletteFor(DisplayMode.NIGHT_RED)
        for (i in 0..10) assertTrue(PaletteSpec.isRedOnly(f(i / 10f)))
    }
    @Test fun sepiaRunsFromParchmentToInk() {
        val f = Spectrogram.paletteFor(DisplayMode.SUNLIGHT)
        assertEquals(0xE8DCC4, f(0f) and 0xFFFFFF); assertEquals(0x2A231D, f(1f) and 0xFFFFFF)
        val d = Spectrogram.paletteFor(DisplayMode.FOREST_DUSK)
        assertEquals(0x2A241C, d(0f) and 0xFFFFFF)
        assertTrue(PaletteSpec.luminance(d(1f)) > PaletteSpec.luminance(d(0.5f)))
    }
}

class ExcerptTest {
    @Test fun excerptDropsWikiLinksAndHashtags() {
        assertEquals("Planted garlic in bed 3.", Readouts.excerpt("Planted garlic in bed 3. [[Vault: Bed_3_Log]] #agronomy"))
        assertEquals("Check the spring box", Readouts.excerpt("Check the  spring box #water"))
        assertEquals("Fix fence & gate.", Readouts.excerpt("Fix fence & gate."))
    }
}

class DuskMapTest {
    @Test fun duskInvertTurnsPaperIntoWarmDarkAndInkIntoCream() {
        val m = ThemeLogic.DARK_INVERT_MATRIX
        val paper = ThemeLogic.apply(m, 250, 250, 245)
        assertTrue("paper should go dark: ${paper.toList()}", paper[0] < 70)
        assertTrue("loden: green >= red, red >= blue ${paper.toList()}", paper[1] >= paper[0] && paper[0] >= paper[2])
        val ink = ThemeLogic.apply(m, 20, 20, 20)
        assertTrue("ink should go cream: ${ink.toList()}", ink[0] > 200 && ink[0] >= ink[1] && ink[1] >= ink[2])
        val green = ThemeLogic.apply(m, 200, 230, 190); val purple = ThemeLogic.apply(m, 200, 160, 220)
        // hue-stable: no output is more blue than red
        assertTrue(green[2] <= green[0] && purple[2] <= purple[0])
    }
}

class NightGlareTest {
    @Test fun nightMatricesAreRedOnlyAndNeverFullBrightness() {
        for (m in listOf(ThemeLogic.RED_620_INVERT_MATRIX, ThemeLogic.RED_620_DIM_MATRIX)) {
            for (px in listOf(intArrayOf(255, 255, 255), intArrayOf(0, 0, 0), intArrayOf(30, 200, 60))) {
                val o = ThemeLogic.apply(m, px[0], px[1], px[2])
                assertEquals(0, o[1]); assertEquals(0, o[2]); assertTrue("red ${o[0]} too bright", o[0] <= 160)
            }
        }
        // inverted: white paper -> near black, black ink -> dim red
        assertTrue(ThemeLogic.apply(ThemeLogic.RED_620_INVERT_MATRIX, 255, 255, 255)[0] < 10)
        assertTrue(ThemeLogic.apply(ThemeLogic.RED_620_INVERT_MATRIX, 0, 0, 0)[0] > 120)
    }
}

class SpectroScaleTest {
    @Test fun normalisesToTheClipsOwnRange() {
        val frames = Array(10) { x -> FloatArray(8) { y -> -80f + x * 2f + y } } // -80 .. -55 dB
        val r = SpectroScale.range(frames)
        assertTrue(r.first >= -80f && r.second <= -55f && r.second > r.first)
        assertEquals(0f, SpectroScale.norm(-90f, r), 0f); assertEquals(1f, SpectroScale.norm(0f, r), 0f)
        assertTrue(SpectroScale.norm(-67f, r) in 0.3f..0.7f)
    }
    @Test fun silenceDoesNotExplodeContrast() {
        val r = SpectroScale.range(Array(4) { FloatArray(4) { -90f } })
        assertEquals(6f, r.second - r.first, 1e-6f)
    }
}

class DuskStreetTest {
    @Test fun osmSurveyAtDuskIsPineDarkWithCreamLabels() {
        val m = ThemeLogic.tileMatrix(DisplayMode.FOREST_DUSK, imagery = false, street = true)
        assertSame(ThemeLogic.DUSK_SURVEY_MATRIX, m)
        val road = ThemeLogic.apply(m, 255, 255, 255)
        val land = ThemeLogic.apply(m, 242, 239, 233); val casing = ThemeLogic.apply(m, 190, 190, 190); val label = ThemeLogic.apply(m, 50, 50, 50)
        assertTrue("land is dark, not khaki ${land.toList()}", land[0] < 50 && land[1] < 55)
        assertTrue("casings separate from land", casing[1] - land[1] > 40)
        assertTrue("roads read as darker channels in land ${road.toList()} vs ${land.toList()}", land[1] - road[1] >= 12)
        assertTrue("labels cream ${label.toList()}", label[0] > 180 && label[0] >= label[1] && label[1] >= label[2])
        fun argb(px: IntArray) = (0xFF shl 24) or (px[0] shl 16) or (px[1] shl 8) or px[2]
        assertTrue("labels >= 7:1 on land", PaletteSpec.contrast(argb(label), argb(land)) >= 7.0)
    }
}

class LightWordingTest {
    @Test fun lightWordingIsTheSameEverywhereAndNeverClashesWithModeNames() {
        assertEquals("0 lx · Dark", Readouts.light(0f)); assertEquals("812 lx · Deep shade", Readouts.light(812.7f)); assertEquals("--", Readouts.light(null))
        val bands = listOf(0f, 50f, 500f, 5_000f, 20_000f, 80_000f).map { ThemeLogic.canopyCategory(it) }
        for (b in bands) for (m in listOf("Sunlight", "Dusk", "Night")) assertFalse("$b reuses mode name $m", b.contains(m, ignoreCase = true))
        assertEquals("Sunlight mode", Readouts.modeTag(DisplayMode.SUNLIGHT, false))
        assertEquals("Auto · Night red mode", Readouts.modeTag(DisplayMode.NIGHT_RED, true))
    }
}

class CardMediaTest {
    private val st = EnvStamp(java.time.ZonedDateTime.parse("2026-10-27T06:48:00-04:00").toInstant().toEpochMilli(), 38.5225, -78.4350, zone = "Demo Farm (open)",
        azimuth = 172.0, pitch = -38.0, baroHpa = 1013.25)
    private fun e(title: String, body: String, photo: String? = null, audio: String? = null) =
        Entry(1, title, body, Category.GENERAL, emptyList(), emptyList(), emptyList(), st, photo, audio)

    @Test fun autoCaptionsAreNotRepeated() {
        assertTrue(Readouts.bodyRepeatsTitle(e("Photo @ Demo Farm (open)", "Photo @ Demo Farm (open)", photo = "x.jpg")))
        assertTrue(Readouts.bodyRepeatsTitle(e("Acoustic clip", "Acoustic clip 8s @ Demo Farm (open)", audio = "a.wav")))
        assertTrue(Readouts.bodyRepeatsTitle(e("Acoustic Clip", "Acoustic clip 8s @ Demo Farm (open)", audio = "a.wav")))
        assertTrue(Readouts.bodyRepeatsTitle(e("Sighting @ Weather Station", "Sighting @ Weather Station")))
        assertFalse(Readouts.bodyRepeatsTitle(e("Plot 3: Winter Wheat Trial", "Planted 42 plots 4 in deep.")))
        // A long typed note becomes title (first 60 chars) + body: the body adds the rest, so it stays.
        val long = "Fresh coyote prints by the lower treeline heading north east toward the station, check the fence tonight"
        assertFalse(Readouts.bodyRepeatsTitle(e(long.take(60), long)))
    }
    @Test fun mediaLineIsFieldDataNotTheTitle() {
        val a = Readouts.mediaLine(e("Acoustic clip", "x", audio = "a.wav"), Readouts.wavSeconds(44 + 8 * 32000L))
        assertEquals("8.0 s · 16 kHz mono · 38.52250°N 78.43500°W · 1013 hPa", a)
        val ph = Readouts.mediaLine(e("Photo @ Demo Farm (open)", "x", photo = "p.jpg"), null)
        assertEquals("Brg 172° S · pitch -38° · 38.52250°N 78.43500°W · 1013 hPa", ph)
        assertFalse(ph.contains("Demo Farm"))
        assertEquals("", Readouts.mediaLine(e("t", "b", photo = "p.jpg").copy(stamp = EnvStamp(0L)), null))
        assertNull(Readouts.wavSeconds(44))
    }
    @Test fun plateCellsSplitLabelValueUnit() {
        val r = Readouts.of(st.copy(baroTrend = "↓", elevRelM = -2.04, ridgeTempF = 38.4), -14.0, 1.6)
        assertEquals(listOf("Baro ↓", "Elev", "Temp", "GDD"), r.plate.map { it.label })
        assertEquals(listOf("1013.3", "-2.0", "38", "--"), r.plate.map { it.value })
        assertEquals(listOf("hPa", "m", "°F", ""), r.plate.map { it.unit })
        assertTrue(Readouts.of(EnvStamp(0L), null, 1.6).plate.all { it.value == "--" && it.unit == "" })
    }
}

class PinClusterTest {
    private val d = 2.75 // Pixel 4a density
    private fun pins(vararg ll: Pair<Double, Double>) = ll.mapIndexed { i, (a, b) -> PinClusters.Pin(i.toLong(), a, b) }
    @Test fun overlappingPinsMergeAndSeparatePinsStayApart() {
        // ~3 m apart (overlap at z16) and one ~400 m away.
        val ps = pins(38.52250 to -78.43500, 38.52252 to -78.43497, 38.52610 to -78.43500)
        val c = PinClusters.group(ps, 16.0, 256 * d, 30 * d)
        assertEquals(2, c.size); assertEquals(listOf(0L, 1L), c[0].ids); assertEquals(listOf(2L), c[1].ids)
        assertEquals(1, PinClusters.group(ps, 10.0, 256 * d, 30 * d).size)
        assertEquals(3, PinClusters.group(ps, 23.0, 256 * d, 30 * d).size)
    }
    @Test fun chainsAreTransitiveAndCentroidIsMean() {
        val ps = pins(38.0 to -78.0, 38.0 to -77.99995, 38.0 to -77.9999)
        val c = PinClusters.group(ps, 16.0, 256 * d, 30 * d)
        assertEquals(1, c.size); assertEquals(-77.99995, c[0].lon, 1e-9)
    }
    @Test fun worldPxMatchesWebMercator() {
        val (x, y) = PinClusters.worldPx(0.0, 0.0, 0.0, 256.0); assertEquals(128.0, x, 1e-9); assertEquals(128.0, y, 1e-9)
    }
}
