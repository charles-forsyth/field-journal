package io.github.charlesforsyth.fieldjournal

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.charlesforsyth.fieldjournal.core.*
import io.github.charlesforsyth.fieldjournal.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

data class Ray(val lat: Double, val lon: Double, val az: Double)

enum class HwEvent { SNAP, PTT_DOWN, PTT_UP }

class AppVm(app: Application) : AndroidViewModel(app) {
    val prefs = Prefs(app)
    val store = Store(app)
    val net = Net(prefs)
    val sensors = SensorHub(app, prefs)

    private val _entries = MutableStateFlow<List<Entry>>(emptyList())
    val entries: StateFlow<List<Entry>> = _entries
    private val _zones = MutableStateFlow(Properties.builtIn)
    val zones: StateFlow<List<PropertyZone>> = _zones

    val displayPref = MutableStateFlow(prefs.displayPref)
    val mode: StateFlow<DisplayMode> = combine(displayPref, sensors.state) { p, s -> ThemeLogic.resolve(p, s.lux) }
        .distinctUntilChanged().stateIn(viewModelScope, SharingStarted.Eagerly, ThemeLogic.resolve(prefs.displayPref, null))

    val telemetry = MutableStateFlow<StationTelemetry?>(null)
    val stationOnline = MutableStateFlow(false)
    val toast = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val hwEvents = MutableSharedFlow<HwEvent>(extraBufferCapacity = 8)
    val speechText = MutableStateFlow("")
    val listening = MutableStateFlow(false)

    val rayA = MutableStateFlow<Ray?>(null)
    val rayB = MutableStateFlow<Ray?>(null)
    val pinnedTarget = MutableStateFlow<LatLon?>(null)
    val journalFilter = MutableStateFlow("")
    val mapFocus = MutableStateFlow<LatLon?>(null)
    val tab = MutableStateFlow(0)
    private val dismissedReminders = mutableSetOf<Long>()

    val activeReminders: StateFlow<List<Entry>> = combine(entries, sensors.state) { list, s ->
        if (s.lat == null || s.lon == null) emptyList()
        else list.filter { it.geoReminder && it.id !in dismissedReminders && it.stamp.lat != null &&
            GeoReminders.isTriggered(GeoReminder(it.id, it.title, it.stamp.lat, it.stamp.lon!!, it.reminderRadiusM), s.lat, s.lon) }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    init {
        refresh()
        viewModelScope.launch { while (true) { pollStation(); delay(60_000) } }
    }

    fun refresh() = viewModelScope.launch(Dispatchers.IO) {
        _entries.value = store.all()
        _zones.value = store.userZones() + Properties.builtIn // user zones first = override
    }

    suspend fun pollStation() {
        val t = net.station()
        stationOnline.value = t != null
        if (t != null) telemetry.value = t
    }

    fun setDisplay(p: DisplayPref) { prefs.displayPref = p; displayPref.value = p }
    fun dismissReminder(id: Long) { dismissedReminders += id; _entries.value = _entries.value.toList() }

    fun liveStamp(): EnvStamp = EnvStampCapture.withTelemetry(
        EnvStampCapture.hardware(sensors.state.value, prefs, sensors.trend3h(), zones.value), telemetry.value)

    fun createEntry(text: String, title: String? = null, photo: String? = null, audio: String? = null,
                    reminder: Boolean = false, radius: Double = 15.0, target: LatLon? = null, onDone: (Long) -> Unit = {}) =
        viewModelScope.launch {
            val stamp = EnvStampCapture.full(sensors.state.value, prefs, sensors.trend3h(), zones.value, net, telemetry.value)
            val parsed = NoteParser.parse(text)
            val zoneLink = zones.value.firstOrNull { it.name == stamp.zone }?.let { "[[Vault: ${it.vaultNote}]]" }
            val links = (parsed.vaultLinks + listOfNotNull(zoneLink)).distinct()
            val t = title?.takeIf { it.isNotBlank() } ?: text.lineSequence().firstOrNull()?.take(60)?.ifBlank { null }
                ?: "${parsed.category.label} @ ${stamp.zone}"
            var e = Entry(0, if (reminder && !t.startsWith("Geo-Reminder")) "Geo-Reminder: $t" else t, text, parsed.category,
                parsed.tags, links, parsed.measurements, stamp, photo, audio, reminder, radius, target?.lat, target?.lon)
            val id = withContext(Dispatchers.IO) { store.insert(e) }
            e = e.copy(id = id)
            refresh(); toast.tryEmit("Logged ${parsed.category.emoji} ${e.title.take(32)}"); onDone(id)
            // Optional AI enrichment (only if a gateway key is set) — appended asynchronously.
            if (prefs.gatewayKey.isNotBlank()) {
                val img = photo?.let { p -> runCatching { thumbB64(p) }.getOrNull() }
                net.aiTags(text, img)?.let { extra ->
                    val merged = (e.tags + extra).distinct()
                    if (merged.size != e.tags.size) { withContext(Dispatchers.IO) { store.update(e.copy(tags = merged)) }; refresh() }
                }
            }
        }

    private fun thumbB64(path: String): String {
        val o = android.graphics.BitmapFactory.Options().apply { inSampleSize = 8 }
        val bmp = android.graphics.BitmapFactory.decodeFile(path, o)
        val bos = java.io.ByteArrayOutputStream(); bmp.compress(android.graphics.Bitmap.CompressFormat.JPEG, 70, bos)
        return android.util.Base64.encodeToString(bos.toByteArray(), android.util.Base64.NO_WRAP)
    }

    fun deleteEntry(id: Long) = viewModelScope.launch(Dispatchers.IO) { store.delete(id); refresh() }
    fun updateEntry(e: Entry) = viewModelScope.launch(Dispatchers.IO) { store.update(e); refresh() }

    fun lockRay(which: Int) {
        val s = sensors.state.value
        if (s.lat == null || s.lon == null || s.azimuth == null) { toast.tryEmit("Need GPS fix + compass to lock ray"); return }
        val r = Ray(s.lat, s.lon, s.azimuth)
        if (which == 1) { rayA.value = r; rayB.value = null; pinnedTarget.value = null; toast.tryEmit("RAY A locked AZ %.0f°".format(r.az)) }
        else {
            val a = rayA.value ?: run { toast.tryEmit("Lock RAY A first"); return }
            rayB.value = r
            if (GeoMath.haversineM(a.lat, a.lon, r.lat, r.lon) < 5) { toast.tryEmit("Walk ≥5 m from Point A for a usable baseline"); return }
            val t = GeoMath.triangulateRays(a.lat, a.lon, a.az, r.lat, r.lon, r.az)
            if (t == null) toast.tryEmit("Rays don't converge — re-sight target")
            else {
                pinnedTarget.value = t
                val d = GeoMath.haversineM(r.lat, r.lon, t.lat, t.lon)
                createEntry("Triangulated target %.0f m from Point B. A az %.0f°, B az %.0f°".format(d, a.az, r.az),
                    title = "Triangulated Sighting", target = t)
            }
        }
    }

    fun clearRays() { rayA.value = null; rayB.value = null; pinnedTarget.value = null }

    fun calibrateBaseline() {
        val h = sensors.state.value.hpa
        if (h == null) toast.tryEmit("No barometer reading") else { prefs.baselineHpa = h; toast.tryEmit("Base elevation = %.2f hPa".format(h)) }
    }

    fun addZoneHere(name: String, radius: Double) {
        val s = sensors.state.value
        if (s.lat == null || s.lon == null) { toast.tryEmit("Need GPS fix"); return }
        viewModelScope.launch(Dispatchers.IO) { store.addZone(PropertyZone(name, "Custom", s.lat, s.lon, radius)); refresh() }
    }
    fun deleteZone(name: String) = viewModelScope.launch(Dispatchers.IO) { store.deleteZone(name); refresh() }

    /** NFC tag / QR zone code -> jump to that bed/asset's history. */
    fun handleTag(tagId: String) = viewModelScope.launch {
        val bound = withContext(Dispatchers.IO) { store.nfcZone(tagId) }
        if (bound != null) { journalFilter.value = bound; tab.value = 2; toast.tryEmit("NFC → $bound") }
        else { journalFilter.value = ""; pendingNfcBind.value = tagId; tab.value = 2 }
    }
    val pendingNfcBind = MutableStateFlow<String?>(null)
    fun bindTag(tag: String, zone: String) = viewModelScope.launch(Dispatchers.IO) {
        store.bindNfc(tag, zone); pendingNfcBind.value = null; journalFilter.value = zone; toast.tryEmit("Bound tag → $zone")
    }

    fun exportDir(ctx: Context) = File(ctx.getExternalFilesDir(null), "Field_Journal").apply { mkdirs() }

    /** Writes .md bundle (+ JSON + zip). Returns shareable zip URI. */
    suspend fun exportBundle(ctx: Context): Uri? = withContext(Dispatchers.IO) {
        val dir = exportDir(ctx); dir.listFiles()?.forEach { it.delete() }
        val list = store.all()
        list.forEach { File(dir, MarkdownExport.fileName(it)).writeText(MarkdownExport.toMarkdown(it)) }
        File(dir, "field_journal.json").writeText(toJson(list))
        val zip = File(ctx.cacheDir, "Field_Journal_${System.currentTimeMillis() / 1000}.zip")
        ZipOutputStream(zip.outputStream()).use { z ->
            dir.listFiles()?.forEach { f -> z.putNextEntry(ZipEntry("Field_Journal/${f.name}")); f.inputStream().copyTo(z); z.closeEntry() }
        }
        if (prefs.mqttEnabled) {
            val n = net.mqttPublish(list)
            toast.tryEmit(if (n >= 0) "MQTT published $n → beacon/journal/${prefs.clientId}" else "MQTT unreachable (bundle still saved)")
        }
        toast.tryEmit("Exported ${list.size} .md → ${dir.absolutePath}")
        FileProvider.getUriForFile(ctx, ctx.packageName + ".files", zip)
    }

    fun toJson(list: List<Entry>): String {
        val arr = org.json.JSONArray()
        list.forEach { e ->
            val s = e.stamp
            arr.put(org.json.JSONObject().put("id", e.id).put("title", e.title).put("body", e.body).put("category", e.category.label)
                .put("date", e.dateIso).put("zone", s.zone).put("lat", s.lat).put("lon", s.lon).put("azimuth", s.azimuth)
                .put("elevation_m", s.elevRelM).put("temp_f", s.ridgeTempF).put("gdd", s.gdd).put("baro_hpa", s.baroHpa)
                .put("lux", s.lux?.toDouble()).put("tags", org.json.JSONArray(e.tags)).put("vault_links", org.json.JSONArray(e.vaultLinks))
                .put("geo_reminder", e.geoReminder).put("target_lat", e.targetLat).put("target_lon", e.targetLon))
        }
        return arr.toString(2)
    }

    override fun onCleared() { sensors.stop(); store.close() }
}
