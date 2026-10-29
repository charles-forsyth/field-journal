package io.github.charlesforsyth.fieldjournal.core

import kotlin.math.PI
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.tan

/**
 * Screen-space pin clustering for the property map (v1.2). Pins closer than [thresholdPx] on screen at the current
 * zoom are grouped; a group of 2+ is drawn as one numbered stamp whose tap opens a list. Greedy single-link in input
 * order, so results are stable between frames. Pure Kotlin (Web Mercator maths) so it is unit-tested without a MapView.
 */
object PinClusters {
    data class Pin(val id: Long, val lat: Double, val lon: Double)
    data class Cluster(val ids: List<Long>, val lat: Double, val lon: Double) { val size get() = ids.size }

    /** World pixel coordinates at [zoom] for a tile edge of [tilePx] pixels. */
    fun worldPx(lat: Double, lon: Double, zoom: Double, tilePx: Double): Pair<Double, Double> {
        val n = tilePx * 2.0.pow(zoom)
        val x = (lon + 180.0) / 360.0 * n
        val r = Math.toRadians(lat.coerceIn(-85.0511, 85.0511))
        val y = (1.0 - ln(tan(r) + 1.0 / kotlin.math.cos(r)) / PI) / 2.0 * n
        return x to y
    }

    fun group(pins: List<Pin>, zoom: Double, tilePx: Double, thresholdPx: Double): List<Cluster> {
        val pts = pins.map { it to worldPx(it.lat, it.lon, zoom, tilePx) }
        val parent = IntArray(pts.size) { it }
        fun find(i: Int): Int { var x = i; while (parent[x] != x) x = parent[x]; return x }
        val t2 = thresholdPx * thresholdPx
        for (i in pts.indices) for (j in i + 1 until pts.size) {
            val dx = pts[i].second.first - pts[j].second.first; val dy = pts[i].second.second - pts[j].second.second
            if (dx * dx + dy * dy < t2) { val a = find(i); val b = find(j); if (a != b) parent[maxOf(a, b)] = minOf(a, b) }
        }
        return pts.indices.groupBy { find(it) }.toSortedMap().values.map { idx ->
            val members = idx.map { pts[it].first }
            Cluster(members.map { it.id }, members.map { it.lat }.average(), members.map { it.lon }.average())
        }
    }
}
