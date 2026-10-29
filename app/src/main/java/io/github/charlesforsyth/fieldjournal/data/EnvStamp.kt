package io.github.charlesforsyth.fieldjournal.data

import io.github.charlesforsyth.fieldjournal.core.*
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Zero-typing Environmental Stamp capture. Hardware values are taken synchronously from the
 * SensorHub; telemetry station (2.5 s) and soil enrichment run in parallel and are bounded so entry creation
 * never blocks for long — unreachable values become null ("--").
 */
object EnvStampCapture {
    fun hardware(s: SensorState, prefs: Prefs, trend: String, zones: List<PropertyZone>): EnvStamp {
        val pitch = s.pitch
        return EnvStamp(
            timeMs = System.currentTimeMillis(),
            lat = s.lat, lon = s.lon, gpsAccM = s.accM,
            zone = ZoneResolver.label(s.lat, s.lon, zones),
            baroHpa = s.hpa?.toDouble(), baroTrend = trend,
            elevRelM = s.hpa?.let { GeoMath.pressureToRelativeMeters(it.toDouble(), prefs.baselineHpa.toDouble()) },
            azimuth = s.azimuth, pitch = pitch,
            estDistM = pitch?.let { GeoMath.estimateGroundDistance(prefs.eyeHeightM.toDouble(), it) },
            lux = s.lux,
        )
    }

    fun withTelemetry(st: EnvStamp, t: StationTelemetry?) = if (t == null) st else st.copy(
        ridgeTempF = t.ridgeTempF, coopTempF = t.coopTempF, freezeMarginF = t.freezeMarginF, windGustMph = t.windGustMph, gdd = t.gdd,
    )

    suspend fun full(s: SensorState, prefs: Prefs, trend: String, zones: List<PropertyZone>, net: Net, cachedTelemetry: StationTelemetry?): EnvStamp = coroutineScope {
        val base = hardware(s, prefs, trend, zones)
        val tel = async { withTimeoutOrNull(2600) { net.station() } ?: cachedTelemetry }
        val soil = async { if (s.lat != null && s.lon != null) withTimeoutOrNull(3000) { net.soil(s.lat, s.lon) } else null }
        val st = withTelemetry(base, tel.await())
        val sp = soil.await()
        st.copy(soilTempC = sp?.first, soilMoisture = sp?.second)
    }
}
