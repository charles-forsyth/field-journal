package io.github.charlesforsyth.fieldjournal.core

import kotlin.math.*

object Fft {
    fun isPow2(n: Int) = n > 0 && (n and (n - 1)) == 0

    /** In-place iterative Cooley-Tukey radix-2 FFT. */
    fun fft(re: DoubleArray, im: DoubleArray) {
        val n = re.size
        require(isPow2(n) && im.size == n) { "size must be power of two" }
        var j = 0
        for (i in 1 until n) {
            var bit = n shr 1
            while (j and bit != 0) { j = j xor bit; bit = bit shr 1 }
            j = j xor bit
            if (i < j) { var t = re[i]; re[i] = re[j]; re[j] = t; t = im[i]; im[i] = im[j]; im[j] = t }
        }
        var len = 2
        while (len <= n) {
            val ang = -2 * PI / len
            val wr = cos(ang); val wi = sin(ang)
            var i = 0
            while (i < n) {
                var cr = 1.0; var ci = 0.0
                for (k in 0 until len / 2) {
                    val a = i + k; val b = a + len / 2
                    val xr = re[b] * cr - im[b] * ci
                    val xi = re[b] * ci + im[b] * cr
                    re[b] = re[a] - xr; im[b] = im[a] - xi
                    re[a] += xr; im[a] += xi
                    val nr = cr * wr - ci * wi; ci = cr * wi + ci * wr; cr = nr
                }
                i += len
            }
            len = len shl 1
        }
    }

    /** Magnitude spectrum (n/2 bins) with optional Hann window, normalised so a full-scale sine ≈ 1.0 (rect). */
    fun magnitude(samples: DoubleArray, hann: Boolean = true): DoubleArray {
        val n = samples.size
        val re = DoubleArray(n) { i -> samples[i] * if (hann) 0.5 - 0.5 * cos(2 * PI * i / (n - 1)) else 1.0 }
        val im = DoubleArray(n)
        fft(re, im)
        val norm = if (hann) n / 4.0 else n / 2.0
        return DoubleArray(n / 2) { k -> hypot(re[k], im[k]) / norm }
    }

    fun binHz(k: Int, n: Int, sampleRate: Int) = k.toDouble() * sampleRate / n
}

/**
 * Display normalisation for the waterfall: maps dB to 0..1 against the clip's own range (2nd..99.5th percentile)
 * instead of a fixed -90..0, so quiet field recordings still show structure. Silence stays flat.
 */
object SpectroScale {
    fun range(frames: Array<FloatArray>): Pair<Float, Float> {
        val all = frames.flatMap { it.asList() }.sorted()
        if (all.isEmpty()) return -90f to 0f
        val lo = all[(all.size * 0.02).toInt().coerceIn(0, all.size - 1)]
        val hi = all[(all.size * 0.995).toInt().coerceIn(0, all.size - 1)]
        return if (hi - lo < 6f) (hi - 6f) to hi else lo to hi
    }
    fun norm(db: Float, r: Pair<Float, Float>) = ((db - r.first) / (r.second - r.first)).coerceIn(0f, 1f)
}

object Spectro {
    const val SAMPLE_RATE = 16_000
    const val FFT_N = 512
    const val HOP = 256

    fun hzToMel(hz: Double) = 2595.0 * log10(1 + hz / 700.0)
    fun melToHz(m: Double) = 700.0 * (10.0.pow(m / 2595.0) - 1)

    /** Returns frames x melBins matrix of dB values (−90..0). */
    fun melWaterfall(pcm: ShortArray, melBins: Int = 64): Array<FloatArray> {
        val frames = max(0, (pcm.size - FFT_N) / HOP + 1)
        val maxMel = hzToMel(SAMPLE_RATE / 2.0)
        val binMap = IntArray(FFT_N / 2) { k ->
            val mel = hzToMel(Fft.binHz(k, FFT_N, SAMPLE_RATE))
            min(melBins - 1, (mel / maxMel * melBins).toInt())
        }
        return Array(frames) { f ->
            val s = DoubleArray(FFT_N) { pcm[f * HOP + it] / 32768.0 }
            val mag = Fft.magnitude(s)
            val out = FloatArray(melBins) { -90f }
            for (k in mag.indices) {
                val db = (20 * log10(mag[k] + 1e-9)).toFloat().coerceIn(-90f, 0f)
                if (db > out[binMap[k]]) out[binMap[k]] = db
            }
            // fill empty low bins by carrying forward
            for (m in 1 until melBins) if (out[m] == -90f) out[m] = out[m - 1]
            out
        }
    }

    /** Simulated test waveform: spring-flow pink-ish noise + a descending bird chirp. */
    fun simulated(seconds: Double = 6.0, seed: Long = 42): ShortArray {
        val n = (seconds * SAMPLE_RATE).toInt()
        val rnd = java.util.Random(seed)
        var lp = 0.0; var phase = 0.0
        return ShortArray(n) { i ->
            val t = i.toDouble() / SAMPLE_RATE
            lp = lp * 0.97 + rnd.nextGaussian() * 0.03
            val cyc = t % 1.5
            val chirp = if (cyc < 0.35) { val f = 4200 - 6000 * cyc; phase += 2 * PI * f / SAMPLE_RATE; 0.35 * sin(phase) } else 0.0
            val hum = 0.08 * sin(2 * PI * 120 * t)
            ((lp * 2 + chirp + hum).coerceIn(-1.0, 1.0) * 30000).toInt().toShort()
        }
    }
}
