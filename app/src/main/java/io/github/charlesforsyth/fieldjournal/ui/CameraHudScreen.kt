package io.github.charlesforsyth.fieldjournal.ui

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.view.View
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import io.github.charlesforsyth.fieldjournal.AppVm
import io.github.charlesforsyth.fieldjournal.HwEvent
import io.github.charlesforsyth.fieldjournal.MainActivity
import io.github.charlesforsyth.fieldjournal.core.*
import java.io.File
import kotlin.math.cos
import kotlin.math.sin

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CameraHudScreen(vm: AppVm) {
    val ctx = LocalContext.current
    val p = LocalPal.current
    val s by vm.sensors.state.collectAsState()
    val tel by vm.telemetry.collectAsState()
    val entries by vm.entries.collectAsState()
    val zones by vm.zones.collectAsState()
    val rayA by vm.rayA.collectAsState()
    val rayB by vm.rayB.collectAsState()
    val target by vm.pinnedTarget.collectAsState()
    val speech by vm.speechText.collectAsState()
    val listening by vm.listening.collectAsState()
    val camGranted = ContextCompat.checkSelfPermission(ctx, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
    val imageCapture = remember { ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY).build() }
    var ghostId by remember { mutableStateOf<Long?>(null) }
    var ghostAlpha by remember { mutableFloatStateOf(0.30f) }
    var note by remember { mutableStateOf("") }
    var cameraOk by remember { mutableStateOf(camGranted) }
    var ghostSheet by remember { mutableStateOf(false) }
    LaunchedEffect(speech) { if (speech.isNotBlank()) { note = (note + " " + speech).trim(); vm.speechText.value = "" } }

    val stamp = remember(s, tel, zones) { vm.liveStamp() }
    val zone = stamp.zone
    val ghostCandidates = entries.filter { it.photoPath != null && File(it.photoPath).exists() && (it.stamp.zone == zone || zone.endsWith("(open)") || zone == "NO FIX") }
    val ghost = entries.firstOrNull { it.id == ghostId }
    val ghostBmp = remember(ghost?.photoPath) { ghost?.photoPath?.let { runCatching { BitmapFactory.decodeFile(it, BitmapFactory.Options().apply { inSampleSize = 4 }) }.getOrNull() } }

    fun snap() {
        if (!cameraOk) { vm.createEntry(note.ifBlank { "Sighting @ $zone" }); note = ""; return }
        val dir = File(ctx.getExternalFilesDir(null), "photos").apply { mkdirs() }
        val f = File(dir, "FJ_${System.currentTimeMillis()}.jpg")
        imageCapture.takePicture(ImageCapture.OutputFileOptions.Builder(f).build(), ContextCompat.getMainExecutor(ctx),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(o: ImageCapture.OutputFileResults) { vm.createEntry(note.ifBlank { "Photo @ $zone" }, photo = f.absolutePath); note = "" }
                override fun onError(e: ImageCaptureException) { vm.toast.tryEmit("Camera error: ${e.message}") }
            })
    }
    LaunchedEffect(Unit) { vm.hwEvents.collect { if (it == HwEvent.SNAP) snap() } }

    val r = Readouts.of(stamp, s.pitch, vm.prefs.eyeHeightM.toDouble())

    Column(Modifier.fillMaxSize()) {
        // ---- Viewfinder: inset like a ground-glass screen ----
        Box(Modifier.weight(1f).fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp)
            .clip(RoundedCornerShape(6.dp)).border(Hair, p.rule, RoundedCornerShape(6.dp))) {
            val matrix = ThemeLogic.RED_620_DIM_MATRIX.takeIf { p.mode == DisplayMode.NIGHT_RED }
            if (cameraOk) {
                val owner = LocalLifecycleOwner.current
                AndroidView(factory = { c ->
                    PreviewView(c).apply {
                        implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                        val fut = ProcessCameraProvider.getInstance(c)
                        fut.addListener({
                            runCatching {
                                val prov = fut.get()
                                val preview = Preview.Builder().build().also { it.setSurfaceProvider(surfaceProvider) }
                                prov.unbindAll()
                                prov.bindToLifecycle(owner, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageCapture)
                            }.onFailure { cameraOk = false }
                        }, ContextCompat.getMainExecutor(c))
                    }
                }, update = { v ->
                    if (matrix != null) v.setLayerType(View.LAYER_TYPE_HARDWARE, Paint().apply { colorFilter = ColorMatrixColorFilter(matrix) })
                    else v.setLayerType(View.LAYER_TYPE_NONE, null)
                }, modifier = Modifier.fillMaxSize())
            } else CompassCard(s.azimuth, s.pitch)

            ghostBmp?.let {
                Image(it.asImageBitmap(), null, Modifier.fillMaxSize().alpha(ghostAlpha), contentScale = ContentScale.Crop,
                    colorFilter = matrix?.let { m -> ColorFilter.colorMatrix(ColorMatrix(m)) })
            }
            Reticle(s.pitch, onCamera = cameraOk)

            OverlayTag("Station · $zone", Modifier.align(Alignment.TopStart).padding(10.dp))
            Column(Modifier.align(Alignment.TopEnd).padding(10.dp), horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (ghost != null) {
                    val gs = ghost.stamp
                    val score = GeoMath.alignmentScore((s.azimuth ?: 0.0) - (gs.azimuth ?: 0.0), (s.pitch ?: 0.0) - (gs.pitch ?: 0.0),
                        if (s.lat != null && gs.lat != null) GeoMath.haversineM(s.lat!!, s.lon!!, gs.lat, gs.lon!!) else 0.0)
                    OverlayTag("Align $score%", color = if (score >= 80) p.secondary else p.heading)
                }
                if (listening) OverlayTag("● Listening", color = p.warn)
            }
            target?.let { OverlayTag("Target %.5f, %.5f".format(it.lat, it.lon), Modifier.align(Alignment.BottomStart).padding(10.dp), color = p.secondary) }
        }

        // ---- Instrument plate ----
        InstrumentPlate(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Reading("Azimuth", r.azimuth, unit = r.cardinal)
                Reading("Pitch", r.pitch)
                Reading("Range", r.range, unit = r.rangeUnit)
            }
            Spacer(Modifier.height(8.dp))
            HairRule(color = p.brass.copy(alpha = 0.35f))
            Spacer(Modifier.height(7.dp))
            // Light is in the masthead; four equal cells, label above value, units smaller (v1.2).
            PlateCells(r.plate)
        }

        // ---- Note + controls ----
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            if (ghost != null) Row(verticalAlignment = Alignment.CenterVertically) {
                GearTag("Ghost", color = p.meta, size = 9)
                RulerSlider(ghostAlpha, { ghostAlpha = it }, 0f..0.6f, Modifier.weight(1f).padding(horizontal = 10.dp), ticks = 6, description = "Ghost frame opacity")
                Mono("${(ghostAlpha * 100).toInt()}%", size = 11, color = p.meta)
            }
            LineField(note, { note = it }, label = "", placeholder = if (listening) "Listening…" else "Field note", singleLine = false,
                trailing = {
                    IconAction(LineIcons.mic, if (listening) "Stop listening" else "Dictate note", tint = if (listening) p.warn else p.primary) {
                        val a = ctx as? MainActivity; if (listening) a?.stopListening() else a?.startListening()
                    }
                })
            if (note.isNotBlank()) {
                val pn = NoteParser.parse(note)
                GearTag((listOf(pn.category.label) + pn.tags.take(3) + pn.measurements).joinToString("  "), color = p.secondary, size = 9)
            }
            Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                RayControl("Ray A", rayA?.az, enabled = true, onClick = { vm.lockRay(1) })
                Spacer(Modifier.width(6.dp))
                RayControl("Ray B", rayB?.az, enabled = rayA != null, onClick = { vm.lockRay(2) })
                if (ghostCandidates.isNotEmpty()) IconAction(LineIcons.ghost, "Ghost frame") { ghostSheet = true }
                Spacer(Modifier.weight(1f))
                PrimaryButton(if (cameraOk) "Snap" else "Log", icon = if (cameraOk) LineIcons.lens else LineIcons.journal) { snap() }
            }
        }
    }

    if (ghostSheet) ActionSheet("Ghost frame", onDismiss = { ghostSheet = false }) {
        SheetRow("None", sub = "Hide the overlay") { ghostId = null; ghostSheet = false }
        ghostCandidates.take(12).forEach { e ->
            SheetRow(e.title, icon = LineIcons.ghost, sub = e.dateIso.substring(0, 10) + " · " + e.stamp.zone) { ghostId = e.id; ghostSheet = false }
        }
    }
}

/**
 * Thin brass reticle: open ring, four short ticks, horizon bar offset by pitch. v1.2: every brass stroke sits on a
 * dark halo (2.6 dp, ~55% ink) with a 1 dp inner keyline, so it reads over bright sky as well as dark foliage.
 */
@Composable
private fun Reticle(pitch: Double?, onCamera: Boolean) {
    val p = LocalPal.current
    Canvas(Modifier.fillMaxSize()) {
        val c = center; val r = size.minDimension * 0.11f; val w = 1.2.dp.toPx()
        val col = if (p.mode == DisplayMode.NIGHT_RED) p.brass else if (p.dark) p.brass else androidx.compose.ui.graphics.Color(0xFFC9A55E)
        val halo = p.halo.copy(alpha = if (onCamera) 0.55f else 0.35f); val hw = 3.4.dp.toPx()
        fun line(a: Offset, b: Offset) { drawLine(halo, a, b, hw, cap = androidx.compose.ui.graphics.StrokeCap.Round); drawLine(col, a, b, w, cap = androidx.compose.ui.graphics.StrokeCap.Round) }
        drawCircle(halo, r, c, style = Stroke(hw)); drawCircle(col, r, c, style = Stroke(w))
        drawCircle(halo, 2.6.dp.toPx(), c); drawCircle(col, 1.5.dp.toPx(), c)
        listOf(Offset(1f, 0f), Offset(-1f, 0f), Offset(0f, 1f), Offset(0f, -1f)).forEach { d ->
            line(Offset(c.x + d.x * r * 1.25f, c.y + d.y * r * 1.25f), Offset(c.x + d.x * r * 1.9f, c.y + d.y * r * 1.9f))
        }
        val dy = ((pitch ?: 0.0) * size.height / 60.0).toFloat().coerceIn(-size.height / 2, size.height / 2)
        val hy = c.y + dy
        line(Offset(c.x - r * 3.2f, hy), Offset(c.x - r * 2.2f, hy))
        line(Offset(c.x + r * 2.2f, hy), Offset(c.x + r * 3.2f, hy))
    }
}

/** Fallback when the camera is denied or absent: a printed compass card with a pitch ladder. */
@Composable
private fun CompassCard(az: Double?, pitch: Double?) {
    val p = LocalPal.current
    val ctx = LocalContext.current
    val tf = remember { runCatching { androidx.core.content.res.ResourcesCompat.getFont(ctx, io.github.charlesforsyth.fieldjournal.R.font.eb_garamond_medium) }.getOrNull() }
    Canvas(Modifier.fillMaxSize().background(p.card)) {
        val c = center; val r = size.minDimension * 0.36f; val w = 1.dp.toPx()
        drawCircle(p.rule, r, c, style = Stroke(w))
        drawCircle(p.rule.copy(alpha = 0.6f), r * 0.80f, c, style = Stroke(w))
        val a0 = -(az ?: 0.0)
        rotate(a0.toFloat(), c) {
            for (d in 0 until 360 step 5) {
                val a = Math.toRadians(d.toDouble()) - Math.PI / 2
                val len = when { d % 90 == 0 -> 0.12f; d % 30 == 0 -> 0.08f; d % 10 == 0 -> 0.05f; else -> 0.025f }
                val col = if (d == 0) p.warn else p.dim
                drawLine(col, Offset(c.x + r * (1 - len) * cos(a).toFloat(), c.y + r * (1 - len) * sin(a).toFloat()),
                    Offset(c.x + r * cos(a).toFloat(), c.y + r * sin(a).toFloat()), if (d % 30 == 0) 1.4f * w else w)
            }
            // Compass rose star (four slim points)
            listOf(0, 90, 180, 270).forEach { d ->
                val a = Math.toRadians(d.toDouble()) - Math.PI / 2; val side = Math.PI / 2
                val tip = Offset(c.x + r * 0.62f * cos(a).toFloat(), c.y + r * 0.62f * sin(a).toFloat())
                val l = Offset(c.x + r * 0.07f * cos(a - side).toFloat(), c.y + r * 0.07f * sin(a - side).toFloat())
                val rr = Offset(c.x + r * 0.07f * cos(a + side).toFloat(), c.y + r * 0.07f * sin(a + side).toFloat())
                val path = androidx.compose.ui.graphics.Path().apply { moveTo(tip.x, tip.y); lineTo(l.x, l.y); lineTo(c.x, c.y); close() }
                val path2 = androidx.compose.ui.graphics.Path().apply { moveTo(tip.x, tip.y); lineTo(rr.x, rr.y); lineTo(c.x, c.y); close() }
                drawPath(path, if (d == 0) p.warn.copy(alpha = 0.85f) else p.primary.copy(alpha = 0.75f))
                drawPath(path2, p.primary.copy(alpha = 0.25f))
            }
            val paint = android.graphics.Paint().apply {
                isAntiAlias = true; textAlign = android.graphics.Paint.Align.CENTER; textSize = 15.dp.toPx(); color = p.heading.toArgb(); typeface = tf
            }
            listOf("N", "E", "S", "W").forEachIndexed { i, t ->
                val a = Math.toRadians(i * 90.0) - Math.PI / 2; val rr = r * 0.72f
                drawContext.canvas.nativeCanvas.drawText(t, c.x + rr * cos(a).toFloat(), c.y + rr * sin(a).toFloat() + 5.dp.toPx(), paint)
            }
        }
        // Lubber line
        drawLine(p.brass, Offset(c.x, c.y - r - 10.dp.toPx()), Offset(c.x, c.y - r + 6.dp.toPx()), 1.5f * w)
        // Pitch ladder on the right margin
        for (k in -3..3) {
            val y = c.y + ((k * 10 + (pitch ?: 0.0)) * size.height / 80.0).toFloat()
            val x0 = size.width - 26.dp.toPx()
            drawLine(p.dim.copy(alpha = if (k == 0) 0.9f else 0.45f), Offset(x0, y), Offset(x0 + (if (k == 0) 14 else 8).dp.toPx(), y), w)
        }
    }
}

