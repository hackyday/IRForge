package io.github.hackyday.irforge.ir

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 3.5 mm "audio jack" IR blasters.
 *
 * Two dongle families are supported (selectable in Settings):
 *  - "envelope": dongle contains a decoder IC that regenerates the IR carrier itself;
 *    we play the mark/space envelope on one channel and DC power on the other.
 *  - "raw": passive LED dongle; we synthesize the actual carrier (needs ≥96 kHz
 *    playback for a real 38 kHz output; falls back to 48 kHz with aliasing).
 *
 * Detection is heuristic (a wired audio output must be present) + user confirmation
 * via the built-in test tone wizard — there is no OS API that identifies an IR dongle.
 */
class AuxIrSink(private val ctx: Context) : IrSink {

    override val key = "aux"
    override val label = "AUX (3.5 mm) IR blaster"

    private val audioManager get() = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    fun wiredOutputPresent(): Boolean = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS).any {
        it.type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES ||
            it.type == AudioDeviceInfo.TYPE_WIRED_HEADSET ||
            it.type == AudioDeviceInfo.TYPE_USB_HEADSET
    }

    override suspend fun available(): Boolean = wiredOutputPresent()

    override suspend fun detail(): String =
        if (wiredOutputPresent())
            "Wired audio output detected. If an IR blaster dongle is plugged in, use the test tone in the Hardware tab."
        else
            "No wired audio output — plug in your 3.5 mm IR blaster dongle."

    /**
     * Plays the pattern. freqHz is the IR carrier (raw mode). envelopeMode: L=data, R=power.
     * Returns the sample rate actually used, or throws on failure.
     */
    suspend fun play(freqHz: Int, pattern: IntArray, envelopeMode: Boolean, channelSwap: Boolean): Int =
        withContext(Dispatchers.IO) {
            for (sr in intArrayOf(96_000, 192_000, 48_000)) {
                try {
                    return@withContext playAt(sr, freqHz, pattern, envelopeMode, channelSwap)
                } catch (_: UnsupportedOperationException) {
                    // try next rate
                } catch (_: IllegalArgumentException) {
                    // try next rate
                }
            }
            throw IllegalStateException("Could not initialize audio output for IR playback")
        }

    override suspend fun transmit(freqHz: Int, pattern: IntArray) {
        // Sink interface default; the engine's aux path uses play() with stored settings.
        play(freqHz, pattern, envelopeMode = true, channelSwap = false)
    }

    private fun playAt(sr: Int, freqHz: Int, pattern: IntArray, envelopeMode: Boolean, swap: Boolean): Int {
        val totalUs = pattern.sum().coerceAtMost(4_000_000) // cap 4 s
        val n = (totalUs.toLong() * sr / 1_000_000L).toInt().coerceAtLeast(1)
        val pcm = ShortArray(n * 2)

        val carrierStep = freqHz.toDouble() / sr
        var phase = 0.0
        var idx = 0
        var remainingUs = totalUs
        val amp = Short.MAX_VALUE * 0.95

        for (i in pattern.indices) {
            if (remainingUs <= 0) break
            val us = pattern[i].coerceAtMost(remainingUs)
            remainingUs -= us
            val len = (us.toLong() * sr / 1_000_000L).toInt()
            val isMark = i % 2 == 0
            for (k in 0 until len) {
                if (idx >= pcm.size) break
                val v: Short = when {
                    !isMark -> 0
                    envelopeMode -> amp.toInt().toShort()
                    else -> {
                        val hi = (phase % 1.0) < 0.5
                        if (hi) amp.toInt().toShort() else (-amp).toInt().toShort()
                    }
                }
                if (!isMark && !envelopeMode) phase += carrierStep * 2 // keep phase advancing in gaps too
                else if (isMark && !envelopeMode) phase += carrierStep
                val l = v
                val r = if (envelopeMode) (if (isMark) amp.toInt().toShort() else 0) else v
                if (!swap) { pcm[idx] = l; pcm[idx + 1] = r } else { pcm[idx] = r; pcm[idx + 1] = l }
                idx += 2
            }
        }

        val chConf = AudioFormat.CHANNEL_OUT_STEREO
        val fmt = AudioFormat.Builder()
            .setSampleRate(sr)
            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
            .setChannelMask(chConf)
            .build()
        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            .setAudioFormat(fmt)
            .setTransferMode(AudioTrack.MODE_STATIC)
            .setBufferSizeInBytes(pcm.size * 2)
            .build()
        try {
            if (track.state != AudioTrack.STATE_INITIALIZED) throw UnsupportedOperationException("sample rate $sr unsupported")
            track.write(pcm, 0, pcm.size)
            // MODE_STATIC loops by default — play exactly once, then stop.
            track.setLoopPoints(0, n, 0)
            track.play()
            Thread.sleep(totalUs / 1000L + 40L)
        } finally {
            runCatching { track.pause() }
            runCatching { track.stop() }
            runCatching { track.release() }
        }
        return sr
    }

    /** Short carrier test tone for the setup wizard. */
    suspend fun playTestTone(envelopeMode: Boolean, swap: Boolean) = play(38_000, testPattern, envelopeMode, swap)

    companion object {
        // NEC power-on style burst for the wizard: audible/visible confirmation on most devices.
        val testPattern = intArrayOf(9000, 4500, 560, 560, 560, 1690, 560, 560, 560, 1690, 560, 40000)
    }
}
