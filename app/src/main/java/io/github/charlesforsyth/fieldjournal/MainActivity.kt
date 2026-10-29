package io.github.charlesforsyth.fieldjournal

import android.Manifest
import android.content.Intent
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.view.KeyEvent
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import io.github.charlesforsyth.fieldjournal.core.DisplayPref
import io.github.charlesforsyth.fieldjournal.core.Readouts
import io.github.charlesforsyth.fieldjournal.ui.*
import kotlinx.coroutines.launch
import org.osmdroid.config.Configuration

class MainActivity : ComponentActivity() {
    private val vm: AppVm by viewModels()
    private var recognizer: SpeechRecognizer? = null
    private var volDownHeld = false

    private val perms = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { vm.sensors.startGps() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Configuration.getInstance().apply {
            userAgentValue = "FieldJournal/${BuildConfig.VERSION_NAME} (io.github.charlesforsyth.fieldjournal)"
            osmdroidBasePath = filesDir.resolve("osmdroid"); osmdroidTileCache = cacheDir.resolve("tiles")
        }
        perms.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO))
        vm.sensors.start()
        lifecycleScope.launch { vm.toast.collect { Toast.makeText(this@MainActivity, it, Toast.LENGTH_SHORT).show() } }
        handleIntent(intent)
        setContent {
            val mode by vm.mode.collectAsState()
            val pal = paletteFor(mode)
            LaunchedEffect(pal) {
                window.statusBarColor = pal.bg.toArgb(); window.navigationBarColor = pal.bg.toArgb()
                androidx.core.view.WindowCompat.getInsetsController(window, window.decorView).apply {
                    isAppearanceLightStatusBars = !pal.dark; isAppearanceLightNavigationBars = !pal.dark
                    // Night: system icons can't be tinted red, so hide the bars (swipe from an edge to reveal).
                    systemBarsBehavior = androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                    if (pal.mode == io.github.charlesforsyth.fieldjournal.core.DisplayMode.NIGHT_RED) hide(androidx.core.view.WindowInsetsCompat.Type.systemBars())
                    else show(androidx.core.view.WindowInsetsCompat.Type.systemBars())
                }
            }
            CompositionLocalProvider(LocalPal provides pal) { FieldTheme(pal) { Root(vm) } }
        }
    }

    override fun onResume() {
        super.onResume()
        runCatching {
            NfcAdapter.getDefaultAdapter(this)?.enableReaderMode(this, { tag -> onTag(tag) },
                NfcAdapter.FLAG_READER_NFC_A or NfcAdapter.FLAG_READER_NFC_B or NfcAdapter.FLAG_READER_NFC_F or NfcAdapter.FLAG_READER_NFC_V or NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK, null)
        }
    }
    override fun onPause() { super.onPause(); runCatching { NfcAdapter.getDefaultAdapter(this)?.disableReaderMode(this) } }
    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); handleIntent(intent) }
    override fun onDestroy() { recognizer?.destroy(); super.onDestroy() }

    private fun onTag(tag: Tag) { val id = tag.id.joinToString("") { "%02X".format(it) }; runOnUiThread { vm.handleTag(id) } }

    private fun handleIntent(i: Intent?) {
        i ?: return
        @Suppress("DEPRECATION") (i.getParcelableExtra<Tag>(NfcAdapter.EXTRA_TAG))?.let { onTag(it) }
        i.data?.takeIf { it.scheme == "fieldjournal" }?.lastPathSegment?.let { vm.handleTag("QR:" + it) }
    }

    // ---- One-handed glove controls ----
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (!vm.prefs.volumeKeys || vm.tab.value != 0) return super.dispatchKeyEvent(event)
        when (event.keyCode) {
            KeyEvent.KEYCODE_VOLUME_UP -> { if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) vm.hwEvents.tryEmit(HwEvent.SNAP); return true }
            KeyEvent.KEYCODE_VOLUME_DOWN -> {
                if (event.action == KeyEvent.ACTION_DOWN && !volDownHeld) { volDownHeld = true; startListening() }
                else if (event.action == KeyEvent.ACTION_UP) { volDownHeld = false; stopListening() }
                return true
            }
        }
        return super.dispatchKeyEvent(event)
    }

    fun startListening() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) { vm.toast.tryEmit("Speech recognition unavailable — type note instead"); return }
        if (recognizer == null) recognizer = SpeechRecognizer.createSpeechRecognizer(this).apply {
            setRecognitionListener(object : RecognitionListener {
                override fun onResults(r: Bundle) {
                    vm.listening.value = false
                    r.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.let { t ->
                        vm.speechText.value = (vm.speechText.value + " " + t).trim()
                    }
                }
                override fun onPartialResults(r: Bundle) {}
                override fun onError(e: Int) { vm.listening.value = false }
                override fun onReadyForSpeech(p: Bundle?) {}
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(v: Float) {}
                override fun onBufferReceived(b: ByteArray?) {}
                override fun onEndOfSpeech() {}
                override fun onEvent(t: Int, p: Bundle?) {}
            })
        }
        vm.listening.value = true
        recognizer?.startListening(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
        })
    }
    fun stopListening() { recognizer?.stopListening() }
}

@Composable
fun Root(vm: AppVm) {
    val p = LocalPal.current
    val tab by vm.tab.collectAsState()
    val reminders by vm.activeReminders.collectAsState()
    Column(Modifier.fillMaxSize().background(p.bg)) {
        Header(vm)
        reminders.forEach { r ->
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp).border(Hair, p.brass, RoundedCornerShape(4.dp))
                .background(p.card, RoundedCornerShape(4.dp)).padding(start = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(LineIcons.flag, null, Modifier.size(16.dp), tint = p.brass)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    GearTag("Geo-reminder · within ${r.reminderRadiusM.toInt()} m", color = p.meta, size = 9)
                    Text(r.title.removePrefix("Geo-Reminder: "), style = Type.bodySmall, color = p.text, maxLines = 1)
                }
                TextAction("Dismiss") { vm.dismissReminder(r.id) }
            }
            LaunchedEffect(r.id) { vm.toast.emit("Within ${r.reminderRadiusM.toInt()} m: ${r.title}") }
        }
        Box(Modifier.weight(1f)) {
            when (tab) {
                0 -> CameraHudScreen(vm)
                1 -> PropertyMapScreen(vm)
                2 -> JournalScreen(vm)
                else -> SettingsScreen(vm)
            }
        }
        BottomNav(tab) { vm.tab.value = it }
    }
}

private val tabs = listOf(LineIcons.lens to "Lens", LineIcons.map to "Map", LineIcons.journal to "Journal", LineIcons.settings to "Settings")

@Composable
fun BottomNav(selected: Int, onSelect: (Int) -> Unit) {
    val p = LocalPal.current
    Column(Modifier.fillMaxWidth().background(p.bg)) {
        HairRule()
        Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 8.dp), horizontalArrangement = Arrangement.SpaceAround) {
            tabs.forEachIndexed { i, (icon, label) ->
                val sel = i == selected
                Column(Modifier.weight(1f).heightIn(min = 56.dp).clickable(role = androidx.compose.ui.semantics.Role.Tab) { onSelect(i) }
                    .semantics { this.selected = sel }.padding(vertical = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    Box(Modifier.width(18.dp).height(1.5.dp).background(if (sel) p.brass else androidx.compose.ui.graphics.Color.Transparent))
                    Spacer(Modifier.height(6.dp))
                    Icon(icon, null, Modifier.size(24.dp), tint = if (sel) p.heading else p.navInactive)
                    Spacer(Modifier.height(3.dp))
                    Text(label.uppercase(), style = Type.tag.copy(fontSize = 9.5.sp), color = if (sel) p.heading else p.navInactive, maxLines = 1)
                }
            }
        }
    }
}

/** Masthead: serif title and a quiet mode line. Display mode itself lives in Settings and on a long-press here. */
@Composable
fun Header(vm: AppVm) {
    val p = LocalPal.current
    val pref by vm.displayPref.collectAsState()
    val mode by vm.mode.collectAsState()
    val s by vm.sensors.state.collectAsState()
    var modeSheet by remember { mutableStateOf(false) }
    // Contours live only in this masthead band; every text block in it sits on a paper knockout (no line crosses text).
    Column(Modifier.fillMaxWidth().statusBarsPadding().topoBand(p).padding(start = 20.dp, end = 8.dp, top = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Column(Modifier.knockout(p)) {
                    Text("Field Journal", style = Type.brand, color = p.heading, maxLines = 1)
                    GearTag("${vm.prefs.homeName} · Offline", color = p.meta, size = 9)
                }
            }
            Column(Modifier.heightIn(min = 48.dp).clickable(onClickLabel = "Change display mode") { modeSheet = true }.padding(horizontal = 12.dp),
                horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.Center) {
                Column(Modifier.knockout(p), horizontalAlignment = Alignment.End) {
                    GearTag(Readouts.modeTag(mode, pref == DisplayPref.AUTO), color = p.primary, size = 9)
                    GearTag(Readouts.light(s.lux), color = p.meta, size = 9)
                }
            }
        }
        Spacer(Modifier.height(8.dp))
    }
    HairRule(Modifier.padding(horizontal = 16.dp))
    if (modeSheet) ActionSheet("Display mode", onDismiss = { modeSheet = false }) {
        DisplayPref.entries.forEach { d ->
            SheetRow(d.label + if (d == pref) "  ✓" else "", sub = when (d) {
                DisplayPref.AUTO -> "Follow the light sensor"
                DisplayPref.SUNLIGHT -> "Cream paper, for daylight"
                DisplayPref.FOREST_DUSK -> "Dark loden, for shade and evening"
                DisplayPref.NIGHT_RED -> "Red only, keeps night vision"
            }) { vm.setDisplay(d); modeSheet = false }
        }
    }
}
