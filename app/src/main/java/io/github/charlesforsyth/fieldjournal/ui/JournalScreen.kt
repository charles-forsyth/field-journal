package io.github.charlesforsyth.fieldjournal.ui

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.charlesforsyth.fieldjournal.AppVm
import io.github.charlesforsyth.fieldjournal.core.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun JournalScreen(vm: AppVm) {
    val ctx = LocalContext.current
    val p = LocalPal.current
    val scope = rememberCoroutineScope()
    val entries by vm.entries.collectAsState()
    val zones by vm.zones.collectAsState()
    val query by vm.journalFilter.collectAsState()
    val pendingTag by vm.pendingNfcBind.collectAsState()
    var catFilter by remember { mutableStateOf<Category?>(null) }
    var recording by remember { mutableStateOf(false) }
    var recSeconds by remember { mutableFloatStateOf(8f) }
    var liveBmp by remember { mutableStateOf<Bitmap?>(null) }
    var recJob by remember { mutableStateOf<Job?>(null) }
    var showRecorder by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    var showJump by remember { mutableStateOf(false) }
    var zoneCode by remember { mutableStateOf("") }

    val filtered = entries.filter { e ->
        (catFilter == null || e.category == catFilter) &&
            (query.isBlank() || listOf(e.title, e.body, e.stamp.zone, e.tags.joinToString(" "), e.vaultLinks.joinToString(" "))
                .any { it.contains(query, ignoreCase = true) })
    }
    val pal: (Float) -> Int = Spectrogram.paletteFor(p.mode)

    fun startRecording() {
        recording = true
        recJob = scope.launch {
            val acc = ArrayList<Short>(16000 * 10)
            var lastRender = 0L
            val pcm = Spectrogram.record(recSeconds.toInt()) { chunk ->
                synchronized(acc) { chunk.forEach { acc.add(it) } }
                val now = System.currentTimeMillis()
                if (now - lastRender > 16) { // ~60 fps cap
                    lastRender = now
                    val snap = synchronized(acc) { acc.toShortArray() }
                    val bmp = Spectrogram.render(Spectro.melWaterfall(snap, 64), pal)
                    scope.launch(Dispatchers.Main) { liveBmp = bmp }
                }
            }
            val f = File(ctx.getExternalFilesDir(null), "audio").apply { mkdirs() }.resolve("FJ_${System.currentTimeMillis()}.wav")
            withContext(Dispatchers.IO) { Spectrogram.saveWav(f, pcm) }
            liveBmp = Spectrogram.render(Spectro.melWaterfall(pcm, 64), pal)
            recording = false
            vm.createEntry("Acoustic clip ${pcm.size / Spectro.SAMPLE_RATE}s @ ${vm.liveStamp().zone}", title = "Acoustic clip", audio = f.absolutePath)
        }
    }
    fun export() = scope.launch {
        vm.exportBundle(ctx)?.let { uri ->
            ctx.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                type = "application/zip"; putExtra(Intent.EXTRA_STREAM, uri); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }, "Send Field_Journal bundle"))
        }
    }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 24.dp)) {
        item {
            Row(Modifier.fillMaxWidth().padding(top = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Field Notes", style = Type.title.copy(fontSize = 24.sp), color = p.heading)
                    GearTag("${filtered.size} of ${entries.size} entries", color = p.meta, size = 9)
                }
                IconAction(LineIcons.more, "More journal actions") { showMenu = true }
            }
            LineField(query, { vm.journalFilter.value = it }, label = "", placeholder = "Search notes, zones, #tags",
                trailing = {
                    if (query.isNotEmpty()) IconAction(LineIcons.close, "Clear search", tint = p.dim, size = 16.dp) { vm.journalFilter.value = "" }
                    else Icon(LineIcons.search, null, Modifier.padding(end = 4.dp).size(18.dp), tint = p.placeholder)
                })
            ScrollFadeRow(Modifier.padding(top = 2.dp)) {
                TabText("All", catFilter == null) { catFilter = null }
                Category.entries.forEach { c -> TabText(c.short, catFilter == c) { catFilter = if (catFilter == c) null else c } }
            }
        }
        pendingTag?.let { tag ->
            item {
                SectionHeader("New tag")
                Text("Tag $tag isn't bound yet. Bind it to:", style = Type.bodySmall, color = p.text)
                FlowRow {
                    (listOf("Jayco 212QB", "Ram") + zones.map { it.name }).distinct().take(10).forEach { z -> TextAction(z) { vm.bindTag(tag, z) } }
                    TextAction("Cancel", color = p.dim) { vm.pendingNfcBind.value = null }
                }
            }
        }
        if (showRecorder || recording) item {
            SectionHeader("Acoustic record")
            Row(verticalAlignment = Alignment.CenterVertically) {
                GearTag("Length", size = 9)
                RulerSlider(recSeconds, { if (!recording) recSeconds = it }, 5f..10f, Modifier.weight(1f).padding(horizontal = 10.dp), steps = 5, ticks = 5,
                    description = "Clip length seconds")
                Mono("${recSeconds.toInt()} s", size = 12, color = p.heading)
            }
            Box(Modifier.fillMaxWidth().padding(top = 8.dp).height(84.dp).clip(RoundedCornerShape(4.dp)).background(p.card)
                .border(Hair, p.rule, RoundedCornerShape(4.dp)), contentAlignment = Alignment.Center) {
                liveBmp?.let { Image(it.asImageBitmap(), "Spectrogram", Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds, filterQuality = FilterQuality.Low) }
                    ?: Text("Mel waterfall appears here", style = Type.italic.copy(fontSize = 14.sp), color = p.dim)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                if (!recording) TextAction("Close", color = p.dim) { showRecorder = false }
                if (recording) PrimaryButton("Stop", icon = LineIcons.stop) { recJob?.cancel(); recording = false }
                else PrimaryButton("Record", icon = LineIcons.record) { startRecording() }
            }
        }
        item { Spacer(Modifier.height(10.dp)); HairRule() }
        items(filtered, key = { it.id }) { e -> EntryCard(vm, e, pal) }
        if (filtered.isEmpty()) item {
            Text("No entries match.", style = Type.italic, color = p.dim, modifier = Modifier.padding(vertical = 28.dp))
        }
    }

    if (showMenu) ActionSheet("Journal", onDismiss = { showMenu = false }) {
        SheetRow("Export Markdown bundle", LineIcons.export, sub = "Obsidian YAML .md + JSON, shared as a zip") { showMenu = false; export() }
        SheetRow("Jump to zone or QR code", LineIcons.tag, sub = "Bed 3, Coop, Spring box…") { showMenu = false; showJump = true }
        SheetRow("Acoustic recorder", LineIcons.record, sub = "5–10 s clip with mel waterfall") { showMenu = false; showRecorder = true }
    }
    if (showJump) ActionSheet("Jump to zone", onDismiss = { showJump = false }) {
        LineField(zoneCode, { zoneCode = it }, label = "Zone or QR code", placeholder = "Bed 3")
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            PrimaryButton("Show entries") { vm.journalFilter.value = zoneCode.trim(); showJump = false }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EntryCard(vm: AppVm, e: Entry, pal: (Float) -> Int) {
    val p = LocalPal.current
    var expanded by remember { mutableStateOf(false) }
    val spec by produceState<Bitmap?>(null, e.audioPath, p.mode) {
        value = e.audioPath?.let { path -> withContext(Dispatchers.Default) { Spectrogram.loadWav(File(path))?.let { Spectrogram.render(Spectro.melWaterfall(it, 64), pal) } } }
    }
    val audioSec by produceState<Double?>(null, e.audioPath) {
        value = e.audioPath?.let { path -> withContext(Dispatchers.IO) { File(path).takeIf { it.exists() }?.let { Readouts.wavSeconds(it.length()) } } }
    }
    val photo by produceState<Bitmap?>(null, e.photoPath) {
        value = e.photoPath?.let { withContext(Dispatchers.IO) { runCatching { BitmapFactory.decodeFile(it, BitmapFactory.Options().apply { inSampleSize = 8 }) }.getOrNull() } }
    }
    Column(Modifier.fillMaxWidth().clickable { expanded = !expanded }.animateContentSize().padding(vertical = 16.dp)) {
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                GearTag(e.category.label, color = p.secondary, size = 9)
                Spacer(Modifier.height(3.dp))
                Text(e.title, style = Type.title, color = p.heading, maxLines = if (expanded) 4 else 2, overflow = TextOverflow.Ellipsis)
            }
            if (e.geoReminder) Row(Modifier.padding(start = 8.dp, top = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(LineIcons.flag, "Geo-reminder", Modifier.size(14.dp), tint = p.brass); Mono("${e.reminderRadiusM.toInt()} m", size = 10, color = p.meta)
            }
        }
        Spacer(Modifier.height(4.dp))
        // Wrap only between fields, never inside one ("41°F" never orphans its separator).
        DotFlow(Readouts.metaLine(e).uppercase().split(" · "))
        photo?.let {
            Image(it.asImageBitmap(), null, Modifier.padding(top = 10.dp).fillMaxWidth().height(150.dp).clip(RoundedCornerShape(3.dp)), contentScale = ContentScale.Crop,
                colorFilter = if (p.mode == DisplayMode.NIGHT_RED) ColorFilter.colorMatrix(ColorMatrix(ThemeLogic.RED_620_DIM_MATRIX)) else null)
        }
        spec?.let {
            Image(it.asImageBitmap(), "Spectrogram", Modifier.padding(top = 10.dp).fillMaxWidth().height(56.dp).clip(RoundedCornerShape(3.dp))
                .border(Hair, p.rule, RoundedCornerShape(3.dp)), contentScale = ContentScale.FillBounds, filterQuality = FilterQuality.Low)
        }
        // Under media: field data, never a repeat of the title (v1.2).
        if (photo != null || spec != null) {
            val ml = Readouts.mediaLine(e, audioSec)
            if (ml.isNotEmpty()) DotFlow(ml.uppercase().split(" · "), Modifier.padding(top = 5.dp))
        }
        // Body only when it adds something beyond the title (auto captions like "Photo @ Demo Farm (open)" are hidden).
        if (!Readouts.bodyRepeatsTitle(e))
            Text(if (expanded) e.body else Readouts.excerpt(e.body), style = Type.body, color = p.text, modifier = Modifier.padding(top = 8.dp),
                maxLines = if (expanded) 60 else 3, overflow = TextOverflow.Ellipsis)
        if (e.tags.isNotEmpty()) Text(e.tags.take(if (expanded) 20 else 4).joinToString("   "), style = Type.bodySmall.copy(fontSize = 12.sp), color = p.primary,
            modifier = Modifier.padding(top = 6.dp), maxLines = if (expanded) 4 else 1, overflow = TextOverflow.Ellipsis)
        if (expanded) {
            if (e.vaultLinks.isNotEmpty()) Text(e.vaultLinks.joinToString("  "), style = Type.readingSmall.copy(fontSize = 11.sp), color = p.secondary, modifier = Modifier.padding(top = 4.dp))
            val s = e.stamp
            SectionHeader("Conditions")
            SpecRow("Position", Readouts.coords(s.lat, s.lon) ?: "--")
            SpecRow("Bearing / pitch", "${s.f(s.azimuth, 0)}° / ${s.f(s.pitch, 0)}°")
            SpecRow("Est. distance", "${s.f(s.estDistM)} m")
            SpecRow("Barometer", "${s.f(s.baroHpa)} hPa ${s.baroTrend}")
            SpecRow("Rel. elevation", "${s.f(s.elevRelM)} m")
            SpecRow("Ridge / coop", "${s.f(s.ridgeTempF, 0)}°F / ${s.f(s.coopTempF, 0)}°F")
            SpecRow("Freeze margin · gust", "${s.f(s.freezeMarginF, 0)}°F · ${s.f(s.windGustMph, 0)} mph")
            SpecRow("Soil", "${s.f(s.soilTempC)}°C · ${s.f(s.soilMoisture, 2)}")
            SpecRow("Light", Readouts.light(s.lux))
            if (e.measurements.isNotEmpty()) SpecRow("Measured", e.measurements.joinToString(", "))
            FlowRow(Modifier.padding(top = 6.dp)) {
                if (s.lat != null) TextAction("Show on map", icon = LineIcons.pin) { vm.mapFocus.value = LatLon(s.lat, s.lon!!); vm.tab.value = 1 }
                TextAction(if (e.geoReminder) "Clear reminder" else "Remind at 15 m", icon = LineIcons.flag) { vm.updateEntry(e.copy(geoReminder = !e.geoReminder)) }
                TextAction("Delete", icon = LineIcons.trash, color = p.warn) { vm.deleteEntry(e.id) }
            }
        }
    }
    HairRule()
}
