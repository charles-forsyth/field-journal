package io.github.charlesforsyth.fieldjournal.data

import io.github.charlesforsyth.fieldjournal.core.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.eclipse.paho.client.mqttv3.MqttClient
import org.eclipse.paho.client.mqttv3.MqttConnectOptions
import org.eclipse.paho.client.mqttv3.MqttMessage
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/** All network I/O. Every call is read-only GET (except opt-in AI gateway + MQTT) and offline-safe. */
class Net(private val prefs: Prefs) {
    private val stationClient = OkHttpClient.Builder().callTimeout(2500, TimeUnit.MILLISECONDS).connectTimeout(2500, TimeUnit.MILLISECONDS).build()
    private val web = OkHttpClient.Builder().callTimeout(8, TimeUnit.SECONDS).build()
    private val ai = OkHttpClient.Builder().callTimeout(30, TimeUnit.SECONDS).build()

    private fun get(c: OkHttpClient, url: String): String? = runCatching {
        c.newCall(Request.Builder().url(url).header("User-Agent", "FieldJournal/1.0").get().build()).execute().use { r ->
            if (r.isSuccessful) r.body?.string() else null
        }
    }.getOrNull()

    suspend fun station(): StationTelemetry? = withContext(Dispatchers.IO) {
        for (base in listOf(prefs.stationPrimary, prefs.stationFallback)) {
            if (base.isBlank()) continue
            for (path in listOf("/api/state", "/api/coop/thermal", "/api/weather")) {
                val body = get(stationClient, base + path) ?: continue
                StationParse.parse(body)?.let { return@withContext it }
            }
        }
        null
    }

    suspend fun soil(lat: Double, lon: Double): Pair<Double?, Double?>? = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        if (prefs.soilEnrich) {
            val url = "https://api.open-meteo.com/v1/forecast?latitude=%.4f&longitude=%.4f&hourly=soil_temperature_6cm,soil_moisture_3_to_9cm&timezone=GMT&forecast_days=2"
                .format(java.util.Locale.US, lat, lon)
            get(web, url)?.let { prefs.soilCache = it }
        }
        prefs.soilCache?.let { StationParse.parseSoil(it, now) }
    }

    /** Optional AI Gateway (OpenAI-compatible) enrichment; returns extra tags or null. */
    suspend fun aiTags(note: String, imageB64: String? = null): List<String>? = withContext(Dispatchers.IO) {
        val key = prefs.gatewayKey
        if (key.isBlank()) return@withContext null
        runCatching {
            val content = JSONArray().put(JSONObject().put("type", "text").put("text",
                "Field notebook observation note. Reply ONLY with up to 6 hashtags (lowercase, e.g. #agronomy/crops, #wildlife/birds), space separated.\nNote: $note"))
            if (imageB64 != null) content.put(JSONObject().put("type", "image_url").put("image_url", JSONObject().put("url", "data:image/jpeg;base64,$imageB64")))
            val body = JSONObject().put("model", if (imageB64 != null) "gpt-4o" else "gpt-4o-mini").put("max_tokens", 80)
                .put("messages", JSONArray().put(JSONObject().put("role", "user").put("content", content)))
            val req = Request.Builder().url(prefs.gatewayUrl + "/chat/completions").header("Authorization", "Bearer $key")
                .post(body.toString().toRequestBody("application/json".toMediaType())).build()
            ai.newCall(req).execute().use { r ->
                if (!r.isSuccessful) return@use null
                val txt = JSONObject(r.body!!.string()).getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content")
                Regex("#[a-z0-9][a-z0-9/_-]*").findAll(txt.lowercase()).map { it.value }.take(6).toList()
            }
        }.getOrNull()
    }

    suspend fun mqttPublish(entries: List<Entry>): Int = withContext(Dispatchers.IO) {
        if (prefs.mqttHost.isBlank()) return@withContext 0
        runCatching {
            val c = MqttClient(prefs.mqttHost, prefs.clientId, MemoryPersistence())
            c.connect(MqttConnectOptions().apply { isCleanSession = true; connectionTimeout = 5 })
            val topic = "beacon/journal/${prefs.clientId}"
            entries.forEach { e ->
                val s = e.stamp
                val js = JSONObject().put("title", e.title).put("date", e.dateIso).put("zone", s.zone).put("lat", s.lat).put("lon", s.lon)
                    .put("category", e.category.label).put("tags", JSONArray(e.tags)).put("body", e.body)
                c.publish(topic, MqttMessage(js.toString().toByteArray()).apply { qos = 1 })
            }
            c.disconnect(); c.close(); entries.size
        }.getOrDefault(-1)
    }
}
