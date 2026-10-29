package io.github.charlesforsyth.fieldjournal.ui

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import io.github.charlesforsyth.fieldjournal.core.Spectro
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.coroutines.coroutineContext

/** Audio capture (16 kHz mono PCM) with simulated fallback + waterfall bitmap rendering. */
object Spectrogram {
    @SuppressLint("MissingPermission")
    suspend fun record(seconds: Int, onChunk: (ShortArray) -> Unit): ShortArray = withContext(Dispatchers.IO) {
        val total = seconds * Spectro.SAMPLE_RATE
        val minBuf = AudioRecord.getMinBufferSize(Spectro.SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val rec = runCatching {
            AudioRecord(MediaRecorder.AudioSource.MIC, Spectro.SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, maxOf(minBuf, 4096))
                .takeIf { it.state == AudioRecord.STATE_INITIALIZED }
        }.getOrNull()
        if (rec == null) {
            // Simulated test waveform streamed in real time so the UI behaves identically.
            val sim = Spectro.simulated(seconds.toDouble())
            var i = 0
            while (i < sim.size && coroutineContext.isActive) {
                val n = minOf(1600, sim.size - i); onChunk(sim.copyOfRange(i, i + n)); i += n
                kotlinx.coroutines.delay(100)
            }
            return@withContext sim
        }
        val out = ShortArray(total); var pos = 0
        val buf = ShortArray(1600)
        rec.startRecording()
        try {
            while (pos < total && coroutineContext.isActive) {
                val n = rec.read(buf, 0, minOf(buf.size, total - pos))
                if (n <= 0) break
                System.arraycopy(buf, 0, out, pos, n); pos += n
                onChunk(buf.copyOf(n))
            }
        } finally { rec.stop(); rec.release() }
        out.copyOf(pos)
    }

    /** Waterfall bitmap: x = time, y = mel (low at bottom). Palette depends on theme. */
    fun render(frames: Array<FloatArray>, palette: (Float) -> Int): Bitmap? {
        if (frames.isEmpty()) return null
        val w = frames.size; val h = frames[0].size
        val px = IntArray(w * h)
        val r = io.github.charlesforsyth.fieldjournal.core.SpectroScale.range(frames)
        for (x in 0 until w) for (y in 0 until h) {
            val v = io.github.charlesforsyth.fieldjournal.core.SpectroScale.norm(frames[x][y], r)
            px[(h - 1 - y) * w + x] = palette(v)
        }
        return Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888)
    }

    fun emeraldPalette(v: Float): Int {
        val r = (v * v * 245).toInt(); val g = (40 + v * 200).toInt().coerceAtMost(255); val b = (30 + v * 100 * (1 - v)).toInt()
        return (0xFF shl 24) or (r shl 16) or (g shl 8) or b
    }
    /** Theme-matched waterfall inks: sepia ink on cream (Sunlight), brass on loden (Dusk), red only (Night). */
    fun paletteFor(m: io.github.charlesforsyth.fieldjournal.core.DisplayMode): (Float) -> Int = when (m) {
        io.github.charlesforsyth.fieldjournal.core.DisplayMode.NIGHT_RED -> ::redPalette
        io.github.charlesforsyth.fieldjournal.core.DisplayMode.SUNLIGHT -> ::sepiaPalette
        io.github.charlesforsyth.fieldjournal.core.DisplayMode.FOREST_DUSK -> ::duskPalette
    }
    private fun lerp(a: Int, b: Int, t: Float) = (a + (b - a) * t).toInt().coerceIn(0, 255)
    private fun ramp(v: Float, stops: Array<IntArray>): Int {
        val x = v.coerceIn(0f, 1f) * (stops.size - 1); val i = x.toInt().coerceAtMost(stops.size - 2); val t = x - i
        val a = stops[i]; val b = stops[i + 1]
        return (0xFF shl 24) or (lerp(a[0], b[0], t) shl 16) or (lerp(a[1], b[1], t) shl 8) or lerp(a[2], b[2], t)
    }
    private val SEPIA = arrayOf(intArrayOf(0xE8, 0xDC, 0xC4), intArrayOf(0xB8, 0xA8, 0x80), intArrayOf(0x6B, 0x7A, 0x55), intArrayOf(0x3E, 0x5B, 0x3F), intArrayOf(0x2A, 0x23, 0x1D))
    private val DUSK = arrayOf(intArrayOf(0x2A, 0x24, 0x1C), intArrayOf(0x5A, 0x50, 0x38), intArrayOf(0x9A, 0xA8, 0x78), intArrayOf(0xD4, 0xB0, 0x6E), intArrayOf(0xF2, 0xEA, 0xDB))
    fun sepiaPalette(v: Float): Int = ramp(v, SEPIA)
    fun duskPalette(v: Float): Int = ramp(Math.pow(v.toDouble(), 0.7).toFloat(), DUSK)
    fun redPalette(v: Float): Int = (0xFF shl 24) or ((Math.pow(v.coerceIn(0f, 1f).toDouble(), 0.7) * 230).toInt() shl 16)
    fun inkPalette(v: Float): Int { val c = (246 - v * 226).toInt(); return (0xFF shl 24) or (c shl 16) or (c shl 8) or (c * 0.9).toInt() }

    fun saveWav(f: File, pcm: ShortArray) {
        val data = ByteBuffer.allocate(pcm.size * 2).order(ByteOrder.LITTLE_ENDIAN).also { b -> pcm.forEach { b.putShort(it) } }.array()
        val hdr = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray()); putInt(36 + data.size); put("WAVE".toByteArray()); put("fmt ".toByteArray())
            putInt(16); putShort(1); putShort(1); putInt(Spectro.SAMPLE_RATE); putInt(Spectro.SAMPLE_RATE * 2); putShort(2); putShort(16)
            put("data".toByteArray()); putInt(data.size)
        }.array()
        f.outputStream().use { it.write(hdr); it.write(data) }
    }

    fun loadWav(f: File): ShortArray? = runCatching {
        val b = f.readBytes(); if (b.size <= 44) return null
        val bb = ByteBuffer.wrap(b, 44, b.size - 44).order(ByteOrder.LITTLE_ENDIAN)
        ShortArray((b.size - 44) / 2) { bb.short }
    }.getOrNull()
}
