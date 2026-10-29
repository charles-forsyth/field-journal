package io.github.charlesforsyth.fieldjournal.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas as ACanvas
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.drawable.BitmapDrawable
import android.graphics.Color as AColor
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.*
import androidx.core.content.res.ResourcesCompat
import io.github.charlesforsyth.fieldjournal.AppVm
import io.github.charlesforsyth.fieldjournal.R
import io.github.charlesforsyth.fieldjournal.core.*
import org.osmdroid.tileprovider.tilesource.OnlineTileSourceBase
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.tileprovider.tilesource.XYTileSource
import org.osmdroid.util.GeoPoint
import org.osmdroid.util.MapTileIndex
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polygon
import org.osmdroid.views.overlay.Polyline

/** USGS ArcGIS tile source — overrides URL to Z/Y/X order. Public domain, no API key. */
class UsgsTileSource(name: String, private val base: String) :
    XYTileSource(name, 0, 16, 256, "", arrayOf(base), "USGS The National Map (public domain)") {
    override fun getTileURLString(index: Long): String =
        TileUrls.arcgis(base, MapTileIndex.getZoom(index), MapTileIndex.getX(index), MapTileIndex.getY(index))
}

/** maxZoom: deepest zoom the server actually has tiles for (USGS returns 404 above z16). */
enum class MapLayer(val label: String, val imagery: Boolean, val maxZoom: Double) {
    USGS_SAT("Aerial", true, 16.0), USGS_TOPO("Topo", false, 16.0), OSM("Survey", false, 19.0)
}

private val sources: Map<MapLayer, OnlineTileSourceBase> by lazy {
    mapOf(
        MapLayer.USGS_SAT to UsgsTileSource("USGSImageryTopo", TileUrls.USGS_IMAGERY_TOPO),
        MapLayer.USGS_TOPO to UsgsTileSource("USGSTopo", TileUrls.USGS_TOPO),
        MapLayer.OSM to TileSourceFactory.MAPNIK,
    )
}

/** Small stamped marker: paper disc, hairline ink ring, serif initial. Cached per (letter, colours, size). */
private object Stamps {
    private val cache = HashMap<String, BitmapDrawable>()
    fun get(ctx: Context, letter: String, ink: Int, paper: Int, ring: Int, halo: Int = ring, dp: Float = 26f): BitmapDrawable {
        val key = "$letter|$ink|$paper|$ring|$halo|$dp"
        return cache.getOrPut(key) {
            val d = ctx.resources.displayMetrics.density; val px = (dp * d).toInt()
            val bmp = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888); val c = ACanvas(bmp)
            val cx = px / 2f; val r = px / 2f - 2.5f * d
            // Contrasting halo (ink colour, soft) so the stamp separates from busy imagery.
            c.drawCircle(cx, cx, r + 1.8f * d, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = halo; alpha = 170 })
            c.drawCircle(cx, cx, r, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = paper })
            c.drawCircle(cx, cx, r, Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 1.2f * d; color = ring })
            c.drawCircle(cx, cx, r - 2.6f * d, Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 0.6f * d; color = ring; alpha = 120 })
            val tp = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = ink; textAlign = Paint.Align.CENTER; textSize = px * 0.44f
                typeface = runCatching { ResourcesCompat.getFont(ctx, R.font.eb_garamond_medium) }.getOrNull()
            }
            c.drawText(letter, cx, cx - (tp.descent() + tp.ascent()) / 2, tp)
            BitmapDrawable(ctx.resources, bmp)
        }
    }
    /** Cluster stamp: double ring with a mono count, same family as the letter stamps. */
    fun count(ctx: Context, n: Int, ink: Int, paper: Int, ring: Int, halo: Int, dp: Float = 30f): BitmapDrawable = cache.getOrPut("n|$n|$ink|$paper|$ring|$halo|$dp") {
        val d = ctx.resources.displayMetrics.density; val px = (dp * d).toInt()
        val bmp = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888); val c = ACanvas(bmp); val cx = px / 2f; val r = px / 2f - 2.5f * d
        c.drawCircle(cx, cx, r + 1.8f * d, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = halo; alpha = 170 })
        c.drawCircle(cx, cx, r, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = paper })
        c.drawCircle(cx, cx, r, Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 1.6f * d; color = ring })
        c.drawCircle(cx, cx, r - 3f * d, Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 0.8f * d; color = ring })
        val tp = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = ink; textAlign = Paint.Align.CENTER; textSize = px * 0.36f
            typeface = runCatching { ResourcesCompat.getFont(ctx, R.font.plex_mono_medium) }.getOrNull()
        }
        c.drawText(n.toString(), cx, cx - (tp.descent() + tp.ascent()) / 2, tp)
        BitmapDrawable(ctx.resources, bmp)
    }
    fun dot(ctx: Context, fill: Int, ring: Int, dp: Float = 14f): BitmapDrawable = cache.getOrPut("dot|$fill|$ring|$dp") {
        val d = ctx.resources.displayMetrics.density; val px = (dp * d).toInt()
        val bmp = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888); val c = ACanvas(bmp); val cx = px / 2f
        c.drawCircle(cx, cx, cx - d, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = ring })
        c.drawCircle(cx, cx, cx - 3.2f * d, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = fill })
        BitmapDrawable(ctx.resources, bmp)
    }
    /** "You" locator: open ring with four outward ticks, drawn *under* stamps so it frames a pin instead of hiding it. */
    fun locator(ctx: Context, ink: Int, halo: Int, dp: Float = 46f): BitmapDrawable = cache.getOrPut("loc|$ink|$halo|$dp") {
        val d = ctx.resources.displayMetrics.density; val px = (dp * d).toInt()
        val bmp = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888); val c = ACanvas(bmp); val cx = px / 2f; val r = 17f * d
        val h = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = halo; alpha = 150; style = Paint.Style.STROKE; strokeWidth = 4.2f * d; strokeCap = Paint.Cap.ROUND }
        val k = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = ink; style = Paint.Style.STROKE; strokeWidth = 2f * d; strokeCap = Paint.Cap.ROUND }
        for (pt in listOf(h, k)) {
            c.drawCircle(cx, cx, r, pt)
            c.drawLine(cx, cx - r - 1f * d, cx, cx - r - 5f * d, pt); c.drawLine(cx, cx + r + 1f * d, cx, cx + r + 5f * d, pt)
            c.drawLine(cx - r - 1f * d, cx, cx - r - 5f * d, cx, pt); c.drawLine(cx + r + 1f * d, cx, cx + r + 5f * d, cx, pt)
        }
        c.drawCircle(cx, cx, 2.6f * d, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = ink })
        BitmapDrawable(ctx.resources, bmp)
    }
    fun cross(ctx: Context, ink: Int, dp: Float = 22f): BitmapDrawable = cache.getOrPut("x|$ink|$dp") {
        val d = ctx.resources.displayMetrics.density; val px = (dp * d).toInt()
        val bmp = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888); val c = ACanvas(bmp); val cx = px / 2f
        val pnt = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = ink; strokeWidth = 1.4f * d; style = Paint.Style.STROKE }
        c.drawCircle(cx, cx, cx * 0.45f, pnt); c.drawLine(cx, d, cx, px - d, pnt); c.drawLine(d, cx, px - d, cx, pnt)
        BitmapDrawable(ctx.resources, bmp)
    }
}

@Composable
fun PropertyMapScreen(vm: AppVm) {
    val ctx = LocalContext.current
    val p = LocalPal.current
    val entries by vm.entries.collectAsState()
    val zones by vm.zones.collectAsState()
    val s by vm.sensors.state.collectAsState()
    val rayA by vm.rayA.collectAsState()
    val rayB by vm.rayB.collectAsState()
    val target by vm.pinnedTarget.collectAsState()
    val focus by vm.mapFocus.collectAsState()
    var layer by remember { mutableStateOf(MapLayer.USGS_TOPO) }
    // One control: the ruler. "Through Dec" includes every month, so it is the "all" position (no separate toggle).
    var month by remember { mutableIntStateOf(11) }
    var mapRef by remember { mutableStateOf<MapView?>(null) }
    var menu by remember { mutableStateOf(false) }
    var keySheet by remember { mutableStateOf(false) }
    var zoom by remember { mutableDoubleStateOf(16.0) }
    var listIds by remember { mutableStateOf<List<Long>?>(null) }

    val visible = Season.visible(entries, month, month == 11)
    val density = ctx.resources.displayMetrics.density
    val clusters = remember(visible, zoom) {
        PinClusters.group(visible.map { PinClusters.Pin(it.id, it.stamp.lat!!, it.stamp.lon!!) }, zoom, 256.0 * density, 30.0 * density)
    }

    Column(Modifier.fillMaxSize()) {
        // ---- Layer tabs + one locate action + overflow ----
        Row(Modifier.fillMaxWidth().padding(start = 12.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            MapLayer.entries.forEach { l -> TabText(l.label, l == layer) { layer = l } }
            Spacer(Modifier.weight(1f))
            IconAction(LineIcons.locate, "Centre on me") {
                if (s.lat != null) mapRef?.controller?.animateTo(GeoPoint(s.lat!!, s.lon!!), layer.maxZoom, 600L) else vm.toast.tryEmit("No GPS fix")
            }
            IconAction(LineIcons.more, "More map actions") { menu = true }
        }
        Box(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 16.dp).clip(RoundedCornerShape(6.dp)).border(Hair, p.rule, RoundedCornerShape(6.dp))) {
            AndroidView(factory = { c ->
                MapView(c).apply {
                    setMultiTouchControls(true); isTilesScaledToDpi = true
                    zoomController.setVisibility(org.osmdroid.views.CustomZoomButtonsController.Visibility.NEVER)
                    controller.setZoom(16.0); controller.setCenter(GeoPoint(vm.prefs.homeLat, vm.prefs.homeLon))
                    addMapListener(object : org.osmdroid.events.MapListener {
                        override fun onScroll(e: org.osmdroid.events.ScrollEvent?) = false
                        override fun onZoom(e: org.osmdroid.events.ZoomEvent?): Boolean { zoom = (e?.zoomLevel ?: zoomLevelDouble).let { Math.round(it * 4) / 4.0 }; return false }
                    })
                    mapRef = this
                }
            }, update = { m ->
                if (m.tileProvider.tileSource != sources[layer]) m.setTileSource(sources[layer])
                m.setMaxZoomLevel(layer.maxZoom + 0.0)
                if (m.zoomLevelDouble > layer.maxZoom) m.controller.setZoom(layer.maxZoom)
                m.overlayManager.tilesOverlay.setColorFilter(ColorMatrixColorFilter(ThemeLogic.tileMatrix(p.mode, layer.imagery, street = layer == MapLayer.OSM)))
                m.overlayManager.tilesOverlay.loadingBackgroundColor = p.card.toArgb()
                m.overlayManager.tilesOverlay.loadingLineColor = p.rule.toArgb()
                m.setBackgroundColor(p.card.toArgb())
                m.overlays.clear()
                val ink = p.heading.toArgb(); val prim = p.primary.toArgb(); val sec = p.secondary.toArgb(); val brass = p.brass.toArgb()
                val paper = p.bg.toArgb(); val night = p.mode == DisplayMode.NIGHT_RED
                zones.forEach { z ->
                    m.overlays.add(Polygon(m).apply {
                        points = Polygon.pointsAsCircle(GeoPoint(z.lat, z.lon), z.radiusM)
                        fillPaint.color = AColor.argb(if (night) 0 else 22, AColor.red(sec), AColor.green(sec), AColor.blue(sec))
                        outlinePaint.color = AColor.argb(130, AColor.red(sec), AColor.green(sec), AColor.blue(sec))
                        outlinePaint.strokeWidth = 1.5f * ctx.resources.displayMetrics.density
                        outlinePaint.pathEffect = android.graphics.DashPathEffect(floatArrayOf(10f, 7f), 0f)
                        title = z.name; infoWindow = null
                    })
                }
                val byId = visible.associateBy { it.id }
                val stampInk = if (p.mode == DisplayMode.FOREST_DUSK) p.text.toArgb() else ink
                val stampRing = if (p.mode == DisplayMode.FOREST_DUSK) brass else prim
                val stampHalo = if (p.mode == DisplayMode.FOREST_DUSK) p.halo.toArgb() else prim
                // Rings first, so stamps always draw on top of them.
                visible.forEach { e ->
                    if (e.geoReminder) m.overlays.add(Polygon(m).apply {
                        points = Polygon.pointsAsCircle(GeoPoint(e.stamp.lat!!, e.stamp.lon!!), e.reminderRadiusM)
                        fillPaint.color = AColor.argb(30, AColor.red(brass), AColor.green(brass), AColor.blue(brass)); outlinePaint.color = brass
                        outlinePaint.strokeWidth = 1.2f * ctx.resources.displayMetrics.density; infoWindow = null
                    })
                    if (e.targetLat != null && e.targetLon != null) m.overlays.add(Marker(m).apply {
                        position = GeoPoint(e.targetLat, e.targetLon); icon = Stamps.cross(ctx, brass); title = "Target: " + e.title
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER); setOnMarkerClickListener { _, _ -> listIds = listOf(e.id); true }
                    })
                }
                if (s.lat != null) m.overlays.add(Marker(m).apply {
                    position = GeoPoint(s.lat!!, s.lon!!); icon = Stamps.locator(ctx, if (night) prim else sec, if (night) paper else p.bg.toArgb())
                    title = "You (±%.0f m)".format(s.accM ?: 0f); setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER); setInfoWindow(null)
                })
                // Overlapping pins collapse into one numbered stamp; tapping any stamp opens the entry list sheet.
                clusters.forEach { cl ->
                    m.overlays.add(Marker(m).apply {
                        position = GeoPoint(cl.lat, cl.lon)
                        icon = if (cl.size > 1) Stamps.count(ctx, cl.size, stampInk, paper, stampRing, stampHalo)
                            else Stamps.get(ctx, byId[cl.ids[0]]!!.category.mark, stampInk, paper, stampRing, halo = stampHalo)
                        title = if (cl.size > 1) "${cl.size} entries" else byId[cl.ids[0]]!!.title
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                        setOnMarkerClickListener { _, _ -> listIds = cl.ids; true }
                    })
                }
                fun ray(r: io.github.charlesforsyth.fieldjournal.Ray) = m.overlays.add(Polyline(m).apply {
                    val end = target?.let { GeoPoint(it.lat, it.lon) } ?: GeoMath.destination(r.lat, r.lon, r.az, 1500.0).let { GeoPoint(it.lat, it.lon) }
                    setPoints(listOf(GeoPoint(r.lat, r.lon), end)); outlinePaint.color = brass
                    outlinePaint.strokeWidth = 1.5f * ctx.resources.displayMetrics.density
                    outlinePaint.pathEffect = android.graphics.DashPathEffect(floatArrayOf(14f, 8f), 0f)
                })
                rayA?.let { ray(it) }; rayB?.let { ray(it) }
                target?.let { t -> m.overlays.add(Marker(m).apply { position = GeoPoint(t.lat, t.lon); icon = Stamps.cross(ctx, brass, 28f); title = "Target"; setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER) }) }
                m.invalidate()
            }, modifier = Modifier.fillMaxSize())
            DisposableEffect(Unit) { onDispose { mapRef?.onDetach(); mapRef = null } }
            LaunchedEffect(focus) { focus?.let { mapRef?.controller?.animateTo(GeoPoint(it.lat, it.lon), layer.maxZoom, 600L); vm.mapFocus.value = null } }
            Text(if (layer == MapLayer.OSM) "© OpenStreetMap contributors" else "USGS The National Map",
                Modifier.align(Alignment.BottomEnd).background(p.bg).padding(horizontal = 5.dp, vertical = 1.dp),
                style = Type.tag.copy(fontSize = 8.sp, letterSpacing = 0.4.sp), color = p.meta)
        }
        // ---- 4D season ruler with GDD curve: one control (tap a month or drag); Dec = all months ----
        Column(Modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp, top = 6.dp, bottom = 2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                GearTag("Season", color = p.secondary)
                Spacer(Modifier.width(10.dp))
                Text(if (month == 11) "All months" else "Jan – ${Season.months[month]}", style = Type.titleSmall.copy(fontSize = 15.sp), color = p.heading, maxLines = 1)
                Spacer(Modifier.width(8.dp))
                Mono("${visible.size} pins", size = 11, color = p.meta, modifier = Modifier.weight(1f))
                TextAction("Key", icon = LineIcons.tag) { keySheet = true }
            }
            SeasonRuler(month, { month = it })
        }
    }

    listIds?.let { ids ->
        val list = ids.mapNotNull { id -> entries.firstOrNull { it.id == id } }
        ActionSheet(if (list.size > 1) "${list.size} entries here" else "Entry", onDismiss = { listIds = null }) {
            list.forEach { e ->
                SheetRow(e.title, sub = "${e.category.label} · ${Readouts.metaLine(e)}", chevron = true) {
                    listIds = null; vm.journalFilter.value = e.title; vm.tab.value = 2
                }
            }
        }
    }
    if (keySheet) ActionSheet("Map key", onDismiss = { keySheet = false }) { MapKey() }

    if (menu) ActionSheet("Map", onDismiss = { menu = false }) {
        SheetRow("Map key", LineIcons.tag, sub = "Pin letters, rings and marks") { menu = false; keySheet = true }
        SectionHeader("Go to")
        SheetRow(vm.prefs.homeName, LineIcons.home, sub = "Home") {
            menu = false; mapRef?.controller?.animateTo(GeoPoint(vm.prefs.homeLat, vm.prefs.homeLon), minOf(16.0, layer.maxZoom), 800L)
        }
        SheetRow(Properties.FIELD_STATION, LineIcons.pin, sub = "Field Station") {
            menu = false; mapRef?.controller?.animateTo(GeoPoint(Properties.STATION_CENTER.lat, Properties.STATION_CENTER.lon), minOf(16.0, layer.maxZoom), 800L)
        }
        SectionHeader("Sight rays")
        SheetRow("Clear sight rays", LineIcons.close, enabled = rayA != null, sub = if (rayA == null) "No rays locked" else "Remove Ray A/B and target") {
            menu = false; vm.clearRays()
        }
    }
}

/** Key for the stamped pin letters and the rings/marks drawn on the map. */
@Composable
private fun MapKey() {
    val p = LocalPal.current
    Column(Modifier.padding(top = 10.dp)) {
        Category.entries.chunked(2).forEach { row ->
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                row.forEach { c ->
                    Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(24.dp).background(p.bg, androidx.compose.foundation.shape.CircleShape)
                            .border(1.2.dp, if (p.mode == DisplayMode.FOREST_DUSK) p.brass else p.primary, androidx.compose.foundation.shape.CircleShape),
                            contentAlignment = Alignment.Center) { Text(c.mark, style = Type.titleSmall.copy(fontSize = 13.sp), color = p.heading) }
                        Spacer(Modifier.width(10.dp)); Text(c.label, style = Type.bodySmall.copy(fontSize = 14.sp), color = p.text)
                    }
                }
            }
        }
        Spacer(Modifier.height(6.dp)); HairRule()
        KeyMark({ androidx.compose.foundation.Canvas(Modifier.size(24.dp)) {
            drawCircle(p.secondary, size.minDimension / 2 - 2.dp.toPx(), style = androidx.compose.ui.graphics.drawscope.Stroke(1.5.dp.toPx(),
                pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 3.5.dp.toPx())))) } },
            "Dashed ring", "Property zone (Garden Bed 3, Coop & Run…)")
        KeyMark({ androidx.compose.foundation.Canvas(Modifier.size(24.dp)) {
            drawCircle(p.brass.copy(alpha = 0.2f), size.minDimension / 2 - 2.dp.toPx()); drawCircle(p.brass, size.minDimension / 2 - 2.dp.toPx(), style = androidx.compose.ui.graphics.drawscope.Stroke(1.2.dp.toPx())) } },
            "Brass ring", "Geo-reminder radius (alerts within 15 m)")
        KeyMark({ Box(Modifier.size(24.dp).background(p.bg, androidx.compose.foundation.shape.CircleShape).border(1.6.dp, p.primary, androidx.compose.foundation.shape.CircleShape),
            contentAlignment = Alignment.Center) { Text("3", style = Type.readingSmall.copy(fontSize = 11.sp), color = p.heading) } },
            "Number", "Overlapping entries - tap for the list")
        KeyMark({ androidx.compose.foundation.Canvas(Modifier.size(24.dp)) {
            val w = 1.4.dp.toPx(); drawCircle(p.brass, size.minDimension * 0.22f, style = androidx.compose.ui.graphics.drawscope.Stroke(w))
            drawLine(p.brass, androidx.compose.ui.geometry.Offset(center.x, 2f), androidx.compose.ui.geometry.Offset(center.x, size.height - 2f), w)
            drawLine(p.brass, androidx.compose.ui.geometry.Offset(2f, center.y), androidx.compose.ui.geometry.Offset(size.width - 2f, center.y), w) } },
            "Cross", "Triangulated target (Ray A × Ray B); dashed brass = sight ray")
        KeyMark({ androidx.compose.foundation.Canvas(Modifier.size(24.dp)) {
            val r = 7.dp.toPx(); val w = 1.6.dp.toPx(); drawCircle(p.secondary, r, style = androidx.compose.ui.graphics.drawscope.Stroke(w)); drawCircle(p.secondary, 1.8.dp.toPx())
            listOf(-1f to 0f, 1f to 0f, 0f to -1f, 0f to 1f).forEach { (dx, dy) ->
                drawLine(p.secondary, androidx.compose.ui.geometry.Offset(center.x + dx * (r + 1f), center.y + dy * (r + 1f)), androidx.compose.ui.geometry.Offset(center.x + dx * (r + 4.dp.toPx()), center.y + dy * (r + 4.dp.toPx())), w) } } },
            "Ticked ring", "You (GPS); frames any pin you're standing on")
    }
}

@Composable
private fun KeyMark(mark: @Composable () -> Unit, name: String, meaning: String) {
    val p = LocalPal.current
    Row(Modifier.fillMaxWidth().heightIn(min = 44.dp), verticalAlignment = Alignment.CenterVertically) {
        mark(); Spacer(Modifier.width(12.dp))
        Column { Text(name, style = Type.bodySmall.copy(fontSize = 14.sp), color = p.text); Text(meaning, style = Type.bodySmall.copy(fontSize = 12.sp), color = p.meta) }
    }
}

/**
 * Season ruler: 12 equal month cells, each a full-height (56 dp) tap target; drag anywhere to scrub. The GDD curve is
 * drawn behind and labelled "GDD"; the selected month is marked with a brass needle and a heavier label; the months
 * after it are de-emphasised by a lighter baseline (not by fading text, to keep every label at AA).
 */
@Composable
private fun SeasonRuler(month: Int, onMonth: (Int) -> Unit) {
    val p = LocalPal.current
    val letters = listOf("J", "F", "M", "A", "M", "J", "J", "A", "S", "O", "N", "D")
    val curve = Season.gddByMonth
    fun idx(x: Float, w: Int) = (x / w * 12).toInt().coerceIn(0, 11)
    Box(Modifier.fillMaxWidth().height(64.dp)
        .semantics {
            contentDescription = "Season, through month"
            stateDescription = if (month == 11) "All months" else "January to ${Season.months[month]}"
            progressBarRangeInfo = ProgressBarRangeInfo(month.toFloat(), 0f..11f, 10)
            setProgress { t -> onMonth(t.toInt().coerceIn(0, 11)); true }
        }
        .pointerInput(Unit) { detectTapGestures { o -> onMonth(idx(o.x, size.width)) } }
        .pointerInput(Unit) { detectHorizontalDragGestures { ch, _ -> onMonth(idx(ch.position.x, size.width)) } }) {
        androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
            val cw = size.width / 12f; val base = size.height - 24.dp.toPx(); val hair = 1.dp.toPx()
            val top = 17.dp.toPx(); val mx = curve.max().toFloat()
            val path = androidx.compose.ui.graphics.Path()
            curve.forEachIndexed { i, v ->
                val x = cw * (i + 0.5f); val y = base - 3.dp.toPx() - v / mx * (base - top - 3.dp.toPx())
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            drawPath(path, p.secondary.copy(alpha = 0.85f), style = androidx.compose.ui.graphics.drawscope.Stroke(1.3f * hair))
            drawLine(p.dim, androidx.compose.ui.geometry.Offset(0f, base), androidx.compose.ui.geometry.Offset(cw * (month + 1), base), 1.6f * hair)
            drawLine(p.rule, androidx.compose.ui.geometry.Offset(cw * (month + 1), base), androidx.compose.ui.geometry.Offset(size.width, base), hair)
            for (i in 0..12) drawLine(p.dim.copy(alpha = 0.7f), androidx.compose.ui.geometry.Offset(cw * i, base), androidx.compose.ui.geometry.Offset(cw * i, base - 5.dp.toPx()), hair)
            val x = cw * (month + 0.5f)
            drawLine(p.brass, androidx.compose.ui.geometry.Offset(x, base + 3.dp.toPx()), androidx.compose.ui.geometry.Offset(x, top - 3.dp.toPx()), 1.6f * hair)
            val tri = androidx.compose.ui.graphics.Path().apply { moveTo(x - 4.dp.toPx(), base + 5.dp.toPx()); lineTo(x + 4.dp.toPx(), base + 5.dp.toPx()); lineTo(x, base); close() }
            drawPath(tri, p.brass)
        }
        // Curve label + value sit above the curve, on the half away from the needle, so neither line crosses the text.
        Row(Modifier.align(if (month >= 6) Alignment.TopStart else Alignment.TopEnd).padding(horizontal = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("GDD", style = Type.tag.copy(fontSize = 9.sp, letterSpacing = 1.2.sp), color = p.curveLabel)
            Spacer(Modifier.width(5.dp))
            Text("${curve[month]} by ${Season.months[month]}", style = Type.readingSmall.copy(fontSize = 10.sp), color = p.curveLabel)
        }
        Row(Modifier.align(Alignment.BottomStart).fillMaxWidth().height(18.dp)) {
            letters.forEachIndexed { i, l ->
                Text(l, Modifier.weight(1f), style = Type.tag.copy(fontSize = 10.sp, letterSpacing = 0.sp,
                    fontWeight = if (i == month) androidx.compose.ui.text.font.FontWeight.Medium else androidx.compose.ui.text.font.FontWeight.Normal),
                    color = if (i == month) p.heading else p.meta, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            }
        }
    }
}
