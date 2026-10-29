package io.github.charlesforsyth.fieldjournal.core

import kotlin.math.*

data class LatLon(val lat: Double, val lon: Double)

object GeoMath {
    const val EARTH_R = 6_371_008.8

    fun haversineM(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2).pow(2) + cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).pow(2)
        return 2 * EARTH_R * asin(min(1.0, sqrt(a)))
    }

    fun bearingDeg(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val p1 = Math.toRadians(lat1); val p2 = Math.toRadians(lat2)
        val dl = Math.toRadians(lon2 - lon1)
        val y = sin(dl) * cos(p2)
        val x = cos(p1) * sin(p2) - sin(p1) * cos(p2) * cos(dl)
        return normalizeAz(Math.toDegrees(atan2(y, x)))
    }

    fun destination(lat: Double, lon: Double, azDeg: Double, distM: Double): LatLon {
        val d = distM / EARTH_R
        val b = Math.toRadians(azDeg)
        val p1 = Math.toRadians(lat); val l1 = Math.toRadians(lon)
        val p2 = asin(sin(p1) * cos(d) + cos(p1) * sin(d) * cos(b))
        val l2 = l1 + atan2(sin(b) * sin(d) * cos(p1), cos(d) - sin(p1) * sin(p2))
        return LatLon(Math.toDegrees(p2), (Math.toDegrees(l2) + 540) % 360 - 180)
    }

    fun normalizeAz(az: Double): Double = ((az % 360.0) + 360.0) % 360.0

    fun cardinal(az: Double): String {
        val names = arrayOf("N", "NNE", "NE", "ENE", "E", "ESE", "SE", "SSE", "S", "SSW", "SW", "WSW", "W", "WNW", "NW", "NNW")
        return names[((normalizeAz(az) + 11.25) / 22.5).toInt() % 16]
    }

    /** 8-point cardinal used in compact HUD readouts (e.g. "AZ 142° SE"). */
    fun cardinal8(az: Double): String {
        val names = arrayOf("N", "NE", "E", "SE", "S", "SW", "W", "NW")
        return names[((normalizeAz(az) + 22.5) / 45.0).toInt() % 8]
    }

    /**
     * Pitch rangefinder. pitchDeg is negative when the camera points below the horizon.
     * Returns null when at/above horizon (no ground intersection).
     */
    fun estimateGroundDistance(eyeHeightM: Double, pitchDeg: Double): Double? {
        if (eyeHeightM <= 0 || pitchDeg >= -0.1 || pitchDeg < -90.0) return null
        return eyeHeightM / tan(-Math.toRadians(pitchDeg))
    }

    /**
     * Two-ray bearing triangulation. Uses a local tangent-plane (ENU) projection around the
     * midpoint of A and B — accurate to centimetres at property/valley scales (< ~20 km).
     * Returns null if rays are (near) parallel or the intersection lies behind either observer.
     */
    fun triangulateRays(lat1: Double, lon1: Double, az1: Double, lat2: Double, lon2: Double, az2: Double): LatLon? {
        val lat0 = (lat1 + lat2) / 2; val lon0 = (lon1 + lon2) / 2
        val kx = EARTH_R * cos(Math.toRadians(lat0)) * PI / 180
        val ky = EARTH_R * PI / 180
        val x1 = (lon1 - lon0) * kx; val y1 = (lat1 - lat0) * ky
        val x2 = (lon2 - lon0) * kx; val y2 = (lat2 - lat0) * ky
        val d1x = sin(Math.toRadians(az1)); val d1y = cos(Math.toRadians(az1))
        val d2x = sin(Math.toRadians(az2)); val d2y = cos(Math.toRadians(az2))
        val denom = d1x * d2y - d1y * d2x
        if (abs(denom) < 1e-6) return null
        val dx = x2 - x1; val dy = y2 - y1
        val t = (dx * d2y - dy * d2x) / denom
        val u = (dx * d1y - dy * d1x) / denom
        if (t < 0 || u < 0) return null
        val px = x1 + t * d1x; val py = y1 + t * d1y
        return LatLon(lat0 + py / ky, lon0 + px / kx)
    }

    /** Ghost-frame alignment score 0..100 from heading/pitch/position deltas vs the reference photo stamp. */
    fun alignmentScore(dAzDeg: Double, dPitchDeg: Double, dPosM: Double): Int {
        var d = abs(dAzDeg) % 360.0; if (d > 180) d = 360 - d
        return (100.0 - d * 2.5 - abs(dPitchDeg) * 3.0 - dPosM * 4.0).coerceIn(0.0, 100.0).roundToInt()
    }

    /** International barometric formula, relative to a calibrated house baseline. */
    fun pressureToRelativeMeters(hPa: Double, baselineHpa: Double): Double {
        if (hPa <= 0 || baselineHpa <= 0) return 0.0
        return 44330.0 * (1.0 - (hPa / baselineHpa).pow(1.0 / 5.255))
    }

    /** Trend over ~3h: returns "↑", "↓" or "→" (±0.5 hPa dead-band, standard synoptic threshold). */
    fun pressureTrend(oldHpa: Double?, nowHpa: Double?): String {
        if (oldHpa == null || nowHpa == null) return "·"
        val d = nowHpa - oldHpa
        return when { d > 0.5 -> "↑"; d < -0.5 -> "↓"; else -> "→" }
    }
}
