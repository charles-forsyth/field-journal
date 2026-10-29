package io.github.charlesforsyth.fieldjournal.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import io.github.charlesforsyth.fieldjournal.core.DisplayPref

class Prefs(ctx: Context) {
    private val p: SharedPreferences = ctx.getSharedPreferences("fj", Context.MODE_PRIVATE)
    private val secure: SharedPreferences? = runCatching {
        val key = MasterKey.Builder(ctx).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
        EncryptedSharedPreferences.create(ctx, "fj_secure", key,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV, EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM)
    }.getOrNull()

    var homeName: String
        get() = p.getString("home_name", "Demo Farm")!!
        set(v) = p.edit().putString("home_name", v.trim()).apply()
    var homeLat: Double
        get() = p.getString("home_lat", "38.5225")!!.toDoubleOrNull() ?: 38.5225
        set(v) = p.edit().putString("home_lat", v.toString()).apply()
    var homeLon: Double
        get() = p.getString("home_lon", "-78.4350")!!.toDoubleOrNull() ?: -78.4350
        set(v) = p.edit().putString("home_lon", v.toString()).apply()

    var displayPref: DisplayPref
        get() = runCatching { DisplayPref.valueOf(p.getString("display", "AUTO")!!) }.getOrDefault(DisplayPref.AUTO)
        set(v) = p.edit().putString("display", v.name).apply()
    var stationPrimary: String
        get() = p.getString("station1", "")!!
        set(v) = p.edit().putString("station1", v.trim().trimEnd('/')).apply()
    var stationFallback: String
        get() = p.getString("station2", "")!!
        set(v) = p.edit().putString("station2", v.trim().trimEnd('/')).apply()
    var gatewayUrl: String
        get() = p.getString("gw", "https://api.openai.com/v1")!!
        set(v) = p.edit().putString("gw", v.trim().trimEnd('/')).apply()
    var mqttHost: String
        get() = p.getString("mqtt", "")!!
        set(v) = p.edit().putString("mqtt", v.trim()).apply()
    var mqttEnabled: Boolean
        get() = p.getBoolean("mqttOn", false)
        set(v) = p.edit().putBoolean("mqttOn", v).apply()
    var baselineHpa: Float
        get() = p.getFloat("baseline", 1013.25f)
        set(v) = p.edit().putFloat("baseline", v).apply()
    var eyeHeightM: Float
        get() = p.getFloat("eye", 1.6f)
        set(v) = p.edit().putFloat("eye", v).apply()
    var volumeKeys: Boolean
        get() = p.getBoolean("volKeys", true)
        set(v) = p.edit().putBoolean("volKeys", v).apply()
    var soilEnrich: Boolean
        get() = p.getBoolean("soil", true)
        set(v) = p.edit().putBoolean("soil", v).apply()
    var soilCache: String?
        get() = p.getString("soilCache", null)
        set(v) = p.edit().putString("soilCache", v).apply()
    val clientId: String
        get() = p.getString("cid", null) ?: ("fj-" + java.util.UUID.randomUUID().toString().take(8)).also { p.edit().putString("cid", it).apply() }

    /** Stored ONLY in EncryptedSharedPreferences; never in plain prefs. */
    var gatewayKey: String
        get() = secure?.getString("gwKey", "") ?: ""
        set(v) { secure?.edit()?.putString("gwKey", v.trim())?.apply() }
    val secureAvailable get() = secure != null

    /** Pressure history (time:hPa) for 3-hour trend. */
    fun pushPressure(t: Long, hpa: Float) {
        val old = pressureHistory()
        if (old.isNotEmpty() && t - old.last().first < 60_000L) return // at most one sample/minute
        val hist = old.filter { t - it.first < 4 * 3_600_000L } + (t to hpa)
        p.edit().putString("phist", hist.takeLast(240).joinToString(";") { "${it.first}:${it.second}" }).apply()
    }
    fun pressureHistory(): List<Pair<Long, Float>> = (p.getString("phist", "") ?: "").split(";").mapNotNull {
        val s = it.split(":"); if (s.size == 2) s[0].toLongOrNull()?.let { a -> s[1].toFloatOrNull()?.let { b -> a to b } } else null
    }
}
