package io.github.charlesforsyth.fieldjournal.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.github.charlesforsyth.fieldjournal.AppVm
import io.github.charlesforsyth.fieldjournal.BuildConfig
import io.github.charlesforsyth.fieldjournal.core.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(vm: AppVm) {
    val ctx = LocalContext.current
    val p = LocalPal.current
    val scope = rememberCoroutineScope()
    val s by vm.sensors.state.collectAsState()
    val pref by vm.displayPref.collectAsState()
    val mode by vm.mode.collectAsState()
    val online by vm.stationOnline.collectAsState()
    val tel by vm.telemetry.collectAsState()
    val zones by vm.zones.collectAsState()
    var homeName by remember { mutableStateOf(vm.prefs.homeName) }
    var homeLat by remember { mutableStateOf(vm.prefs.homeLat.toString()) }
    var homeLon by remember { mutableStateOf(vm.prefs.homeLon.toString()) }
    var url1 by remember { mutableStateOf(vm.prefs.stationPrimary) }
    var url2 by remember { mutableStateOf(vm.prefs.stationFallback) }
    var gw by remember { mutableStateOf(vm.prefs.gatewayUrl) }
    var key by remember { mutableStateOf("") }
    val keySet = remember { mutableStateOf(vm.prefs.gatewayKey.isNotBlank()) }
    var mqtt by remember { mutableStateOf(vm.prefs.mqttHost) }
    var mqttOn by remember { mutableStateOf(vm.prefs.mqttEnabled) }
    var eye by remember { mutableStateOf(vm.prefs.eyeHeightM.toString()) }
    var vol by remember { mutableStateOf(vm.prefs.volumeKeys) }
    var soil by remember { mutableStateOf(vm.prefs.soilEnrich) }
    var zoneName by remember { mutableStateOf("") }
    var zoneR by remember { mutableStateOf("10") }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
        Text("Specifications", style = Type.title.copy(fontSize = androidx.compose.ui.unit.TextUnit(24f, androidx.compose.ui.unit.TextUnitType.Sp)),
            color = p.heading, modifier = Modifier.padding(top = 14.dp))
        Text("Readings, calibration, display and sync", style = Type.italic, color = p.meta)

        // ---- Readings: read-only, on a parchment plate, units in their own column ----
        SectionHeader("Readings")
        ReadingsPlate {
            val lux = s.lux
            LedgerRow("Light", lux?.toInt()?.toString() ?: if (s.hasLight) "…" else "--", unit = "lx",
                sub = if (lux != null) ThemeLogic.canopyCategory(lux) + " · " + Readouts.modeTag(mode, pref == DisplayPref.AUTO) else if (!s.hasLight) "No sensor" else null)
            LedgerRow("Barometer", s.hpa?.let { "%.2f".format(it) } ?: if (s.hasBaro) "…" else "--", unit = "hPa",
                sub = s.hpa?.let { Readouts.trendWord(vm.sensors.trend3h()).ifEmpty { null }?.replaceFirstChar { c -> c.uppercase() } } ?: if (!s.hasBaro) "No sensor" else null)
            LedgerRow("Rel. elevation", s.hpa?.let { "%+.1f".format(GeoMath.pressureToRelativeMeters(it.toDouble(), vm.prefs.baselineHpa.toDouble())) } ?: "--", unit = "m",
                sub = "vs base elevation")
            // Angles keep the degree sign on the number; the unit column holds the cardinal / hemisphere.
            LedgerRow("Azimuth", s.azimuth?.let { "%03.0f°".format(it) } ?: if (s.hasRotation) "…" else "--", unit = s.azimuth?.let { GeoMath.cardinal8(it) } ?: "")
            LedgerRow("Pitch", s.pitch?.let { "%+.0f°".format(it) } ?: "--")
            LedgerRow("Latitude", s.lat?.let { "%.5f°".format(kotlin.math.abs(it)) } ?: "no fix", unit = s.lat?.let { if (it >= 0) "N" else "S" } ?: "")
            LedgerRow("Longitude", s.lon?.let { "%.5f°".format(kotlin.math.abs(it)) } ?: "no fix", unit = s.lon?.let { if (it >= 0) "E" else "W" } ?: "")
            if (s.lat != null) LedgerRow("GPS accuracy", "±%.0f".format(s.accM ?: 0f), unit = "m")
            LedgerRow("Zone", ZoneResolver.label(s.lat, s.lon, zones), last = true)
        }

        // ---- Calibration: things you set ----
        SectionHeader("Calibration")
        SpecRow("Base elevation pressure", "%.2f".format(vm.prefs.baselineHpa), unit = "hPa", trailing = { FieldButton("Set") { vm.calibrateBaseline() } })
        SpecField("Eye height (rangefinder)", eye, { eye = it; it.toFloatOrNull()?.takeIf { v -> v in 0.5f..3f }?.let { v -> vm.prefs.eyeHeightM = v } }, unit = "m", trailingSpace = TrailCol + 6.dp)

        SectionHeader("Calibration · home property")
        LineField(homeName, { homeName = it; vm.prefs.homeName = it }, label = "Home property name", placeholder = "Demo Farm")
        Row(verticalAlignment = Alignment.Bottom) {
            LineField(homeLat, { homeLat = it; it.toDoubleOrNull()?.let { v -> vm.prefs.homeLat = v } }, label = "Home lat", modifier = Modifier.weight(1f), keyboard = KeyboardType.Number)
            Spacer(Modifier.width(14.dp))
            LineField(homeLon, { homeLon = it; it.toDoubleOrNull()?.let { v -> vm.prefs.homeLon = v } }, label = "Home lon", modifier = Modifier.weight(1f), keyboard = KeyboardType.Number)
            FieldButton("GPS") {
                s.lat?.let { lat -> s.lon?.let { lon ->
                    homeLat = "%.5f".format(lat); homeLon = "%.5f".format(lon)
                    vm.prefs.homeLat = lat; vm.prefs.homeLon = lon
                    vm.toast.tryEmit("Home set to GPS")
                } } ?: vm.toast.tryEmit("No GPS fix")
            }
        }

        SectionHeader("Calibration · zones")
        Text("${zones.size} zones, ${zones.count { it.property == "Custom" }} custom. New zones are centred on your current position.", style = Type.bodySmall, color = p.meta)
        zones.filter { it.property == "Custom" }.forEach { z ->
            SpecRow(z.name, "r ${z.radiusM.toInt()} m", trailing = { IconAction(LineIcons.close, "Delete zone ${z.name}", tint = p.meta, size = 16.dp) { vm.deleteZone(z.name) } })
        }
        Row(verticalAlignment = Alignment.Bottom) {
            LineField(zoneName, { zoneName = it }, label = "New zone", modifier = Modifier.weight(2f), placeholder = "Plot 4")
            Spacer(Modifier.width(14.dp))
            LineField(zoneR, { zoneR = it }, label = "Radius m", modifier = Modifier.weight(1f), keyboard = KeyboardType.Number)
            FieldButton("Add") { if (zoneName.isNotBlank()) { vm.addZoneHere(zoneName.trim(), zoneR.toDoubleOrNull() ?: 10.0); zoneName = "" } }
        }

        SectionHeader("Display")
        Row(Modifier.fillMaxWidth()) {
            DisplayPref.entries.forEach { d -> TabText(d.short, d == pref, Modifier.weight(1f)) { vm.setDisplay(d) } }
        }
        Text("Now in ${Readouts.modeTag(mode, pref == DisplayPref.AUTO)}. Auto uses Sunlight at 10,000 lx and above, Night red below 10 lx, Dusk between.",
            style = Type.bodySmall, color = p.meta)
        ToggleRow("Volume keys", vol, { vol = it; vm.prefs.volumeKeys = it }, sub = "On the Lens: hold Vol− to dictate, Vol+ to snap")

        // ---- Sync ----
        SectionHeader("Sync · sensor station feed (optional)")
        SpecRow("Status", if (online) "Online" else "Offline", valueColor = if (online) p.secondary else p.warn)
        if (online) SpecRow("Ridge · shelter · GDD", "${tel?.ridgeTempF?.let { "%.0f°F".format(it) } ?: "--"} · ${tel?.coopTempF?.let { "%.0f°F".format(it) } ?: "--"} · ${tel?.gdd?.let { "%.0f".format(it) } ?: "--"}")
        LineField(url1, { url1 = it }, label = "Primary feed URL", placeholder = "http://station.example.org:8080", keyboard = KeyboardType.Uri)
        LineField(url2, { url2 = it }, label = "Fallback feed URL", placeholder = "http://station2.example.org:8080", keyboard = KeyboardType.Uri)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextAction("Test") { vm.prefs.stationPrimary = url1; vm.prefs.stationFallback = url2; scope.launch { vm.pollStation(); vm.toast.emit(if (vm.stationOnline.value) "Station reachable" else "Station unreachable") } }
            FieldButton("Save") { vm.prefs.stationPrimary = url1; vm.prefs.stationFallback = url2; vm.toast.tryEmit("Saved") }
        }
        Text("Read-only GET requests, 2.5 s timeout.", style = Type.bodySmall, color = p.meta)
        ToggleRow("Soil enrichment", soil, { soil = it; vm.prefs.soilEnrich = it }, sub = "Open-Meteo soil temperature, cached offline")

        SectionHeader("Sync · AI gateway (optional)")
        Text(if (keySet.value) "A key is stored in encrypted preferences." else "No key. Notes are tagged offline by the built-in parser.", style = Type.bodySmall, color = p.meta)
        if (!vm.prefs.secureAvailable) Text("Keystore unavailable: a key cannot be stored.", style = Type.bodySmall, color = p.warn)
        LineField(gw, { gw = it }, label = "Gateway base URL", placeholder = "https://api.openai.com/v1", keyboard = KeyboardType.Uri)
        LineField(key, { key = it }, label = "Gateway key", password = true, placeholder = if (keySet.value) "•••••• stored" else "Paste key (optional)")
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            if (keySet.value) TextAction("Clear key", color = p.warn) { vm.prefs.gatewayKey = ""; keySet.value = false }
            FieldButton("Save") { vm.prefs.gatewayUrl = gw; if (key.isNotBlank()) { vm.prefs.gatewayKey = key; key = ""; keySet.value = true; vm.toast.tryEmit("Key saved") } else vm.toast.tryEmit("Saved") }
        }

        SectionHeader("Sync · export")
        LineField(mqtt, { mqtt = it; vm.prefs.mqttHost = it }, label = "MQTT broker", placeholder = "mqtt://broker.example.org", keyboard = KeyboardType.Uri)
        ToggleRow("Publish on export", mqttOn, { mqttOn = it; vm.prefs.mqttEnabled = it }, sub = "beacon/journal/${vm.prefs.clientId}")
        Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.End) {
            PrimaryButton("Export Markdown + JSON", icon = LineIcons.export) { scope.launch { vm.exportBundle(ctx) } }
        }
        Text("Saved to Android/data/…/files/Field_Journal", style = Type.bodySmall, color = p.meta, modifier = Modifier.padding(top = 2.dp))

        Spacer(Modifier.height(28.dp))
        HairRule()
        Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Field Journal", style = Type.italic, color = p.meta)
            GearTag("No. ${BuildConfig.VERSION_NAME}", color = p.meta, size = 9)
        }
        Spacer(Modifier.height(32.dp))
    }
}
