package io.github.charlesforsyth.fieldjournal.data

import android.annotation.SuppressLint
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Looper
import io.github.charlesforsyth.fieldjournal.core.GeoMath
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class SensorState(
    val lux: Float? = null,
    val hpa: Float? = null,
    val azimuth: Double? = null,
    val pitch: Double? = null,
    val lat: Double? = null,
    val lon: Double? = null,
    val accM: Float? = null,
    val hasLight: Boolean = false,
    val hasBaro: Boolean = false,
    val hasRotation: Boolean = false,
)

/** Hardware sensor hub: light, pressure, rotation vector, GPS. */
class SensorHub(private val ctx: Context, private val prefs: Prefs) : SensorEventListener, LocationListener {
    private val sm = ctx.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val lm = ctx.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    private val light = sm.getDefaultSensor(Sensor.TYPE_LIGHT)
    private val baro = sm.getDefaultSensor(Sensor.TYPE_PRESSURE)
    private val rot = sm.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
    private val _s = MutableStateFlow(SensorState(hasLight = light != null, hasBaro = baro != null, hasRotation = rot != null))
    val state: StateFlow<SensorState> = _s
    private val rm = FloatArray(9); private val orient = FloatArray(3)
    private var lastPush = 0L

    fun start() {
        light?.let { sm.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL) }
        baro?.let { sm.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL) }
        rot?.let { sm.registerListener(this, it, SensorManager.SENSOR_DELAY_UI) }
        startGps()
    }

    @SuppressLint("MissingPermission")
    fun startGps() {
        runCatching {
            for (p in listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)) {
                if (lm.isProviderEnabled(p)) {
                    lm.requestLocationUpdates(p, 2000L, 1f, this, Looper.getMainLooper())
                    lm.getLastKnownLocation(p)?.let { onLocationChanged(it) }
                }
            }
        }
    }

    fun stop() { sm.unregisterListener(this); runCatching { lm.removeUpdates(this) } }

    override fun onSensorChanged(e: SensorEvent) {
        when (e.sensor.type) {
            Sensor.TYPE_LIGHT -> _s.value = _s.value.copy(lux = e.values[0])
            Sensor.TYPE_PRESSURE -> {
                val v = e.values[0]; _s.value = _s.value.copy(hpa = v)
                val now = System.currentTimeMillis()
                if (now - lastPush > 60_000) { lastPush = now; prefs.pushPressure(now, v) }
            }
            Sensor.TYPE_ROTATION_VECTOR -> {
                SensorManager.getRotationMatrixFromVector(rm, e.values)
                // Remap so pitch reflects the rear camera axis when phone held upright (portrait).
                val out = FloatArray(9)
                SensorManager.remapCoordinateSystem(rm, SensorManager.AXIS_X, SensorManager.AXIS_Z, out)
                SensorManager.getOrientation(out, orient)
                val az = GeoMath.normalizeAz(Math.toDegrees(orient[0].toDouble()))
                val pitch = -Math.toDegrees(orient[1].toDouble())
                val prev = _s.value
                // light low-pass on azimuth (circular)
                val sAz = prev.azimuth?.let { p -> var d = az - p; if (d > 180) d -= 360; if (d < -180) d += 360; GeoMath.normalizeAz(p + d * 0.25) } ?: az
                val sPitch = prev.pitch?.let { it + (pitch - it) * 0.25 } ?: pitch
                _s.value = prev.copy(azimuth = sAz, pitch = sPitch)
            }
        }
    }

    override fun onAccuracyChanged(s: Sensor?, a: Int) {}
    override fun onLocationChanged(l: Location) {
        val cur = _s.value
        if (cur.accM != null && l.provider == LocationManager.NETWORK_PROVIDER && cur.accM < l.accuracy) return
        _s.value = cur.copy(lat = l.latitude, lon = l.longitude, accM = l.accuracy)
    }
    @Deprecated("") override fun onStatusChanged(p: String?, s: Int, b: android.os.Bundle?) {}
    override fun onProviderEnabled(p: String) {}
    override fun onProviderDisabled(p: String) {}

    fun trend3h(): String {
        val h = prefs.pressureHistory(); val now = System.currentTimeMillis()
        val old = h.firstOrNull { now - it.first in 2 * 3_600_000L..4 * 3_600_000L } ?: h.firstOrNull()
        return GeoMath.pressureTrend(old?.second?.toDouble(), _s.value.hpa?.toDouble())
    }
}
