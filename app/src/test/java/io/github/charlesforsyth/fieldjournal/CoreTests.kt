package io.github.charlesforsyth.fieldjournal

import io.github.charlesforsyth.fieldjournal.core.*
import org.junit.Assert.*
import org.junit.Test

class GeoMathTest {
    @Test fun rangefinderBasic() {
        assertEquals(1.6 / Math.tan(Math.toRadians(14.0)), GeoMath.estimateGroundDistance(1.6, -14.0)!!, 1e-9)
        assertEquals(1.6, GeoMath.estimateGroundDistance(1.6, -45.0)!!, 1e-9)
        assertEquals(6.4, GeoMath.estimateGroundDistance(1.6, -Math.toDegrees(Math.atan(0.25)))!!, 1e-6)
    }
    @Test fun rangefinderAboveHorizonIsNull() {
        assertNull(GeoMath.estimateGroundDistance(1.6, 0.0))
        assertNull(GeoMath.estimateGroundDistance(1.6, 10.0))
        assertNull(GeoMath.estimateGroundDistance(0.0, -10.0))
    }
    @Test fun triangulateKnownTarget() {
        val target = LatLon(38.5280, -78.4280)
        val a = LatLon(38.5225, -78.4350); val b = LatLon(38.5230, -78.4200)
        val az1 = GeoMath.bearingDeg(a.lat, a.lon, target.lat, target.lon)
        val az2 = GeoMath.bearingDeg(b.lat, b.lon, target.lat, target.lon)
        val p = GeoMath.triangulateRays(a.lat, a.lon, az1, b.lat, b.lon, az2)!!
        assertTrue("err ${GeoMath.haversineM(p.lat, p.lon, target.lat, target.lon)}", GeoMath.haversineM(p.lat, p.lon, target.lat, target.lon) < 2.0)
    }
    @Test fun triangulateSimpleCross() {
        // A looks due north, B (100m east of A) looks due west-northwest-ish to a point 100m north of A
        val a = LatLon(38.0, -78.0)
        val b = GeoMath.destination(a.lat, a.lon, 90.0, 100.0)
        val p = GeoMath.triangulateRays(a.lat, a.lon, 0.0, b.lat, b.lon, 315.0)!!
        val expected = GeoMath.destination(a.lat, a.lon, 0.0, 100.0)
        assertTrue(GeoMath.haversineM(p.lat, p.lon, expected.lat, expected.lon) < 0.5)
    }
    @Test fun triangulateParallelOrBehindNull() {
        assertNull(GeoMath.triangulateRays(38.0, -78.0, 10.0, 38.0, -77.99, 10.0))
        assertNull(GeoMath.triangulateRays(38.0, -78.0, 180.0, 38.0, -77.99, 315.0))
    }
    @Test fun baroRelative() {
        assertEquals(0.0, GeoMath.pressureToRelativeMeters(1013.25, 1013.25), 1e-9)
        val up = GeoMath.pressureToRelativeMeters(1012.05, 1013.25)
        assertEquals(10.0, up, 0.3) // ~8.3 m/hPa near sea level -> 1.2hPa ≈ 10m
        assertTrue(GeoMath.pressureToRelativeMeters(1014.0, 1013.25) < 0)
        assertEquals(0.0, GeoMath.pressureToRelativeMeters(0.0, 1013.25), 0.0)
    }
    @Test fun baroTrend() {
        assertEquals("↑", GeoMath.pressureTrend(1010.0, 1011.0))
        assertEquals("↓", GeoMath.pressureTrend(1010.0, 1008.0))
        assertEquals("→", GeoMath.pressureTrend(1010.0, 1010.3))
    }
    @Test fun alignment() {
        assertEquals(100, GeoMath.alignmentScore(0.0, 0.0, 0.0))
        assertEquals(95, GeoMath.alignmentScore(358.0, 0.0, 0.0))
        assertEquals(0, GeoMath.alignmentScore(90.0, 10.0, 5.0))
    }
    @Test fun cardinals() {
        assertEquals("SE", GeoMath.cardinal8(142.0))
        assertEquals("N", GeoMath.cardinal8(359.0))
        assertEquals("SSE", GeoMath.cardinal(150.0))
        assertEquals(10.0, GeoMath.normalizeAz(-350.0), 1e-9)
    }
}

class ZoneTest {
    private fun z(n: String) = Properties.builtIn.first { it.name == n }
    @Test fun resolvesDemoPlotsAndStation() {
        for (i in 1..6) { val b = z("Field Plot $i"); assertEquals("Field Plot $i", ZoneResolver.resolve(b.lat, b.lon)!!.name) }
        val c = z("Weather Station"); assertEquals("Weather Station", ZoneResolver.resolve(c.lat, c.lon)!!.name)
        assertEquals(Properties.DEMO_FARM, ZoneResolver.resolve(c.lat, c.lon)!!.property)
    }
    @Test fun resolvesFieldStation() {
        val a = z("Research Cabin"); assertEquals("Research Cabin", ZoneResolver.resolve(a.lat, a.lon)!!.name)
        val p = z("Pond Transect"); val r = ZoneResolver.resolve(p.lat, p.lon)!!
        assertEquals("Pond Transect", r.name); assertEquals(Properties.FIELD_STATION, r.property)
    }
    @Test fun offPropertyAndOpen() {
        assertNull(ZoneResolver.resolve(40.0, -75.0))
        assertEquals("Off-Property", ZoneResolver.label(40.0, -75.0))
        assertEquals("NO FIX", ZoneResolver.label(null, null))
        val p = GeoMath.destination(38.5225, -78.4350, 45.0, 600.0)
        assertEquals("Demo Farm (open)", ZoneResolver.label(p.lat, p.lon))
    }
    @Test fun userZoneOverrides() {
        val custom = Properties.builtIn + PropertyZone("Hoop House", Properties.DEMO_FARM, 38.5225, -78.4350, 3.0)
        assertEquals("Hoop House", ZoneResolver.resolve(38.5225, -78.4350, custom)!!.name)
    }
    @Test fun geoReminderProximity() {
        val r = GeoReminder(1, "Fix fence insulator", 38.5230, -78.4350, 15.0)
        val p14 = GeoMath.destination(r.lat, r.lon, 77.0, 14.0)
        val p16 = GeoMath.destination(r.lat, r.lon, 77.0, 16.0)
        assertTrue(GeoReminders.isTriggered(r, r.lat, r.lon))
        assertTrue(GeoReminders.isTriggered(r, p14.lat, p14.lon))
        assertFalse(GeoReminders.isTriggered(r, p16.lat, p16.lon))
        assertEquals(1, GeoReminders.triggered(listOf(r), p14.lat, p14.lon).size)
    }
}

class NoteParserTest {
    @Test fun garlicKelp() {
        val p = NoteParser.parse("Plot 3 planted Chesnok Red garlic 4 in deep, side-dressed with 2 gal kelp tea. Soil 52°F")
        assertEquals(Category.AGRONOMY, p.category)
        assertTrue(p.tags.containsAll(listOf("#agronomy", "#agronomy/garlic", "#kelp-tea")))
        assertTrue(p.measurements.toString(), p.measurements.containsAll(listOf("4 in", "2 gal", "52°F")))
        assertTrue(p.vaultLinks.contains("[[Vault: Plot_3_Log]]"))
    }
    @Test fun coyote() {
        val p = NoteParser.parse("Coyote tracks by lower treeline, prints 2.5 in wide [[Vault: Predator_Log]] #blink-audit")
        assertEquals(Category.WILDLIFE, p.category)
        assertTrue(p.tags.containsAll(listOf("#predator", "#predator/coyote", "#tracks", "#blink-audit")))
        assertTrue(p.measurements.contains("2.5 in"))
        assertEquals(listOf("[[Vault: Predator_Log]]"), p.vaultLinks)
    }
    @Test fun fleetAndWater() {
        assertEquals(Category.FLEET, NoteParser.parse("Trailer winterization, 3 gal antifreeze").category)
        assertTrue(NoteParser.parse("Trailer maintenance check").tags.contains("#trailer"))
        val w = NoteParser.parse("Spring box flow 4.2 gpm, water 47°F")
        assertEquals(Category.WATER, w.category); assertTrue(w.measurements.containsAll(listOf("4.2 gpm", "47°F")))
        assertEquals(Category.FORAGE, NoteParser.parse("Found chanterelles near the oaks").category)
        assertEquals(Category.GENERAL, NoteParser.parse("hello").category)
    }
    @Test fun noFalseMeasuresFromWords() {
        assertTrue(NoteParser.parse("Plot 3 is good").measurements.isEmpty())
    }
}

class MarkdownTest {
    @Test fun frontmatter() {
        val text = "Plot 3 garlic with kelp tea"
        val p = NoteParser.parse(text)
        val e = Entry(1, "Plot 3: Chesnok Red \"Garlic\"", text, p.category, p.tags, p.vaultLinks, p.measurements,
            EnvStamp(1_760_000_000_000, 38.52, -78.43, 4f, "Field Plot 3", 1012.3, "↑", 2.4, 142.0, -11.0, 8.2, 25000f, 48.0, 51.0, 16.0, 12.0, 2850.0))
        val md = MarkdownExport.toMarkdown(e)
        assertTrue(md.startsWith("---\n"))
        val fm = md.substringAfter("---\n").substringBefore("\n---")
        for (k in listOf("date:", "zone: \"Field Plot 3\"", "lat: 38.520000", "lon: -78.430000", "azimuth: 142.0", "elevation_m: 2.4",
            "temp_f: 48.0", "gdd: 2850", "baro_hpa: 1012.3", "lux: 25000", "tags: [", "\"kelp-tea\"", "vault_links: [\"[[Vault: Plot_3_Log]]\"]"))
            assertTrue("missing $k\n$fm", fm.contains(k))
        assertTrue(fm.contains("title: \"Plot 3: Chesnok Red \\\"Garlic\\\"\""))
        assertTrue(MarkdownExport.fileName(e).endsWith("-plot-3-chesnok-red-garlic.md"))
    }
    @Test fun offlineNulls() {
        val e = Entry(2, "x", "y", Category.GENERAL, emptyList(), emptyList(), emptyList(), EnvStamp(0))
        val md = MarkdownExport.toMarkdown(e)
        assertTrue(md.contains("temp_f: null")); assertTrue(md.contains("tags: []"))
        assertTrue(e.stamp.hudLine().contains("--"))
    }
}

class SeasonScrubberTest {
    private fun at(id: Long, iso: String, lat: Double? = 38.52) = Entry(id, "e$id", "", Category.GENERAL, emptyList(), emptyList(), emptyList(),
        EnvStamp(java.time.ZonedDateTime.parse(iso).toInstant().toEpochMilli(), lat, -78.43))
    @Test fun filtersByMonth() {
        val list = listOf(at(1, "2026-03-15T12:00-04:00"), at(2, "2026-07-04T12:00-04:00"), at(3, "2026-10-01T12:00-04:00"), at(4, "2026-01-01T12:00-05:00", lat = null))
        assertEquals(listOf(1L), Season.visible(list, 3, false).map { it.id })
        assertEquals(listOf(1L, 2L), Season.visible(list, 6, false).map { it.id })
        assertEquals(listOf(1L, 2L, 3L), Season.visible(list, 0, true).map { it.id })
        assertEquals(12, Season.gddByMonth.size)
        assertTrue(Season.gddByMonth.toList().zipWithNext().all { (a, b) -> b >= a })
    }
}

class SpectrogramTest {
    @Test fun pureToneBin() {
        val n = 1024; val sr = 16000; val k = 64 // 1000 Hz exactly on bin
        val s = DoubleArray(n) { Math.sin(2 * Math.PI * k * it / n) }
        val m = Fft.magnitude(s, hann = false)
        assertEquals(n / 2, m.size)
        assertEquals(k, m.indices.maxByOrNull { m[it] })
        assertEquals(1.0, m[k], 1e-6)
        assertEquals(1000.0, Fft.binHz(k, n, sr), 1e-9)
        assertTrue(m[k + 5] < 1e-6)
    }
    @Test fun fftMatchesDft() {
        val n = 16; val rnd = java.util.Random(1)
        val x = DoubleArray(n) { rnd.nextDouble() - 0.5 }
        val re = x.copyOf(); val im = DoubleArray(n); Fft.fft(re, im)
        for (k in 0 until n) {
            var r = 0.0; var i = 0.0
            for (t in 0 until n) { val a = -2 * Math.PI * k * t / n; r += x[t] * Math.cos(a); i += x[t] * Math.sin(a) }
            assertEquals(r, re[k], 1e-9); assertEquals(i, im[k], 1e-9)
        }
    }
    @Test fun dcAndHann() {
        val m = Fft.magnitude(DoubleArray(256) { 0.5 }, hann = false)
        assertEquals(0.5 * 2, m[0], 1e-9) // DC bin scaled by n/2 norm
        val h = Fft.magnitude(DoubleArray(512) { Math.sin(2 * Math.PI * 40 * it / 512) })
        assertEquals(40, h.indices.maxByOrNull { h[it] })
    }
    @Test(expected = IllegalArgumentException::class) fun nonPow2() { Fft.fft(DoubleArray(10), DoubleArray(10)) }
    @Test fun melWaterfallShape() {
        val pcm = Spectro.simulated(1.0)
        val w = Spectro.melWaterfall(pcm, 64)
        assertEquals((16000 - 512) / 256 + 1, w.size)
        assertEquals(64, w[0].size)
        assertTrue(w.all { f -> f.all { it in -90f..0f } })
        assertEquals(1000.0, Spectro.melToHz(Spectro.hzToMel(1000.0)), 1e-6)
    }
}

class ThemeTest {
    @Test fun luxModes() {
        assertEquals(DisplayMode.SUNLIGHT, ThemeLogic.modeForLux(10_000f))
        assertEquals(DisplayMode.SUNLIGHT, ThemeLogic.modeForLux(80_000f))
        assertEquals(DisplayMode.FOREST_DUSK, ThemeLogic.modeForLux(9_999f))
        assertEquals(DisplayMode.FOREST_DUSK, ThemeLogic.modeForLux(10f))
        assertEquals(DisplayMode.NIGHT_RED, ThemeLogic.modeForLux(9.9f))
        assertEquals(DisplayMode.NIGHT_RED, ThemeLogic.modeForLux(0f))
        assertEquals(DisplayMode.FOREST_DUSK, ThemeLogic.modeForLux(null))
    }
    @Test fun manualOverride() {
        assertEquals(DisplayMode.NIGHT_RED, ThemeLogic.resolve(DisplayPref.NIGHT_RED, 50_000f))
        assertEquals(DisplayMode.SUNLIGHT, ThemeLogic.resolve(DisplayPref.SUNLIGHT, 1f))
        assertEquals(DisplayMode.SUNLIGHT, ThemeLogic.resolve(DisplayPref.AUTO, 20_000f))
    }
    @Test fun redMatrixZeroesGreenBlue() {
        val m = ThemeLogic.RED_620_MATRIX
        assertEquals(20, m.size)
        for (i in 5 until 15) assertEquals(0f, m[i], 0f)
        val white = ThemeLogic.apply(m, 255, 255, 255)
        assertEquals(255, white[0]); assertEquals(0, white[1]); assertEquals(0, white[2]); assertEquals(255, white[3])
        val green = ThemeLogic.apply(m, 0, 200, 0)
        assertEquals(0, green[1]); assertTrue(green[0] > 0)
        assertNull(ThemeLogic.matrixFor(DisplayMode.SUNLIGHT))
    }
    @Test fun canopy() {
        assertEquals("Full sun", ThemeLogic.canopyCategory(60_000f))
        assertEquals("Dark", ThemeLogic.canopyCategory(1f))
    }
}

class StationParseTest {
    @Test fun tileUrlsZyxAndKeyless() {
        assertEquals("https://basemap.nationalmap.gov/arcgis/rest/services/USGSTopo/MapServer/tile/15/12000/9000",
            TileUrls.arcgis(TileUrls.USGS_TOPO, 15, 9000, 12000))
        assertEquals("https://tile.openstreetmap.org/3/1/2.png", TileUrls.osm(3, 1, 2))
        assertTrue(TileUrls.all.none { TileUrls.requiresKey(it) })
        assertTrue(TileUrls.requiresKey("https://basemaps.cartocdn.com/x"))
    }
    @Test fun nestedState() {
        val t = StationParse.parse("""{"weather":{"ridge_temp_f":38.5,"wind_gust_mph":22},"coop":{"coop_temp_f":44.1},"gdd":2911.4}""")!!
        assertEquals(38.5, t.ridgeTempF!!, 1e-9); assertEquals(44.1, t.coopTempF!!, 1e-9)
        assertEquals(12.1, t.freezeMarginF!!, 1e-6); assertEquals(22.0, t.windGustMph!!, 1e-9); assertEquals(2911.4, t.gdd!!, 1e-9)
        assertNull(StationParse.parse("not json"))
    }
    @Test fun soil() {
        val js = """{"hourly":{"time":["2026-10-27T00:00","2026-10-27T01:00"],"soil_temperature_6cm":[9.1,8.7],"soil_moisture_3_to_9cm":[0.31,0.30]}}"""
        val ms = java.time.Instant.parse("2026-10-27T01:20:00Z").toEpochMilli()
        assertEquals(8.7 to 0.30, StationParse.parseSoil(js, ms))
    }
}
