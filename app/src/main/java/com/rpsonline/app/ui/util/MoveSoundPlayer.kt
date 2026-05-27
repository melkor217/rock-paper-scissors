package com.rpsonline.app.ui.util

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import com.rpsonline.app.data.model.Move
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.sin

class MoveSoundPlayer {
    private val bursts = mutableMapOf<Move, ShortArray>()
    private var track: AudioTrack? = null
    private var trackKey: Pair<Move, Int>? = null

    fun play(move: Move, repetitions: Int) {
        val count = repetitions.coerceIn(1, 3)
        try {
            val burst = bursts.getOrPut(move) { burstSamplesFor(move) }
            val samples = repeatedSamples(burst, count)
            val key = move to count
            if (trackKey != key || track?.state != AudioTrack.STATE_INITIALIZED) {
                track?.release()
                track = createAudioTrack(samples)
                trackKey = key
            }
            val activeTrack = track ?: return
            activeTrack.stop()
            activeTrack.reloadStaticData()
            activeTrack.play()
        } catch (_: Exception) {
            release()
        }
    }

    fun release() {
        track?.run {
            stop()
            release()
        }
        track = null
        trackKey = null
    }

    private fun createAudioTrack(samples: ShortArray): AudioTrack =
        AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_GAME)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(SAMPLE_RATE)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build(),
            )
            .setBufferSizeInBytes(samples.size * Short.SIZE_BYTES)
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build()
            .apply {
                write(samples, 0, samples.size)
            }

    private fun burstSamplesFor(move: Move): ShortArray = when (move) {
        Move.ROCK -> rockThudSamples()
        Move.PAPER -> paperRustleSamples()
        Move.SCISSORS -> scissorsClickSamples()
    }

    private fun repeatedSamples(burst: ShortArray, count: Int, gapMs: Int = BURST_GAP_MS): ShortArray {
        if (count <= 1) return burst
        val gapSamples = SAMPLE_RATE * gapMs / 1000
        val totalSize = burst.size * count + gapSamples * (count - 1)
        val samples = ShortArray(totalSize)
        var offset = 0
        repeat(count) { index ->
            burst.copyInto(samples, offset)
            offset += burst.size
            if (index < count - 1) {
                offset += gapSamples
            }
        }
        return samples
    }

    private fun rockThudSamples(): ShortArray {
        val numSamples = SAMPLE_RATE * 230 / 1000
        val samples = ShortArray(numSamples)
        for (i in 0 until numSamples) {
            val t = i.toDouble() / SAMPLE_RATE

            val impactEnv = exp(-t * 9.8)
            val thud = sin(2 * PI * 66 * t) * impactEnv
            val subThud = sin(2 * PI * 40 * t) * exp(-t * 6.8) * 0.68

            val crack = sin(2 * PI * 305 * t) * exp(-t * 44) * 0.44 +
                sin(2 * PI * 540 * t) * exp(-t * 68) * 0.20

            val grit = pseudoNoise(i) * exp(-t * 34) * 0.36 +
                pseudoNoise(i * 3 + 401) * exp(-t * 50) * 0.16

            val bounceT = (t - 0.050).coerceAtLeast(0.0)
            val bounce = sin(2 * PI * 112 * bounceT) * exp(-bounceT * 26) * 0.50

            val tail = sin(2 * PI * 52 * t) * exp(-t * 4.8) * 0.30

            val body = thud + subThud + crack + grit + bounce + tail
            val release = ((numSamples.toDouble() / SAMPLE_RATE - t) / 0.09).coerceIn(0.0, 1.0)
            val value = body * release * ROCK_AMPLITUDE * Short.MAX_VALUE
            samples[i] = value.toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }
        return samples
    }

    private fun paperRustleSamples(): ShortArray {
        val numSamples = SAMPLE_RATE * 210 / 1000
        val samples = ShortArray(numSamples)
        for (i in 0 until numSamples) {
            val t = i.toDouble() / SAMPLE_RATE
            val flutter = 0.35 + 0.65 * sin(2 * PI * 38 * t + sin(2 * PI * 9 * t) * 0.8)
            val crisp = 0.25 + 0.75 * sin(2 * PI * 22 * t + 0.4).let { it * it }
            val noiseA = pseudoNoise(i)
            val noiseB = pseudoNoise(i * 5 + 913)
            val noiseC = pseudoNoise(i * 11 + 271)
            val rustle = (noiseA * 0.45 + noiseB * 0.35 + noiseC * 0.20) * flutter * crisp
            val swish = sin(2 * PI * 920 * t) * pseudoNoise(i + 44) * exp(-t * 4.5) * 0.10
            val envelope = fadeInOut(t, numSamples, attackMs = 4.0, releaseStartMs = 150.0)
            val value = (rustle + swish) * envelope * MOVE_AMPLITUDE * Short.MAX_VALUE
            samples[i] = value.toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }
        return samples
    }

    private fun scissorsClickSamples(): ShortArray {
        val numSamples = SAMPLE_RATE * 155 / 1000
        val samples = ShortArray(numSamples)
        for (i in 0 until numSamples) {
            val t = i.toDouble() / SAMPLE_RATE
            val glide = scissorGlide(t)
            val snipA = scissorSnip(t, timeSec = 0.014)
            val snipB = scissorSnip(t, timeSec = 0.070, gain = 0.90)
            val value = (glide + snipA + snipB) * SCISSORS_AMPLITUDE * Short.MAX_VALUE
            samples[i] = value.toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }
        return samples
    }

    /** Brief blade rub as the scissors close. */
    private fun scissorGlide(t: Double): Double {
        val env = exp(-t * 32.0) * (1.0 - exp(-t * 280.0))
        val friction = pseudoNoise((t * SAMPLE_RATE * 1.6).toInt() + 512) * 0.20
        val whine = sin(2 * PI * 2_380 * t + sin(2 * PI * 48 * t) * 0.55) * 0.08
        return (friction + whine) * env
    }

    /** One blade closure: snap + metallic ring. */
    private fun scissorSnip(t: Double, timeSec: Double, gain: Double = 1.0): Double {
        val dt = t - timeSec
        if (dt < 0.0) return 0.0

        val strike = exp(-(dt / 0.0013) * (dt / 0.0013))
        val snap = pseudoNoise((t * SAMPLE_RATE).toInt() + (timeSec * 10_000).toInt()) * strike * 0.50

        val ringHz = 4_350.0 + timeSec * 650.0
        val ringEnv = exp(-dt * 125.0) * strike
        val ring = sin(2 * PI * ringHz * t) * ringEnv * 0.38 +
            sin(2 * PI * ringHz * 2.61 * t) * ringEnv * 0.22 +
            sin(2 * PI * ringHz * 4.18 * t) * ringEnv * 0.11

        val bite = sin(2 * PI * 7_600 * t) * exp(-dt * 480.0) * strike * 0.28

        return (snap + ring + bite) * gain
    }

    private fun fadeInOut(
        tSec: Double,
        numSamples: Int,
        attackMs: Double,
        releaseStartMs: Double,
    ): Double {
        val attack = (tSec / (attackMs / 1000.0)).coerceIn(0.0, 1.0)
        val totalSec = numSamples.toDouble() / SAMPLE_RATE
        val release = ((totalSec - tSec) / ((totalSec * 1000 - releaseStartMs) / 1000.0)).coerceIn(0.0, 1.0)
        return attack * release
    }

    private fun pseudoNoise(index: Int): Double {
        val x = sin(index * 12.9898 + index * 78.233) * 43758.5453
        return (x - floor(x)) * 2.0 - 1.0
    }

    private companion object {
        const val SAMPLE_RATE = 44_100
        const val BURST_GAP_MS = 70
        /** Clock tick peaks around 0.95; move sounds sit a step above. */
        const val MOVE_AMPLITUDE = 1.0
        const val ROCK_AMPLITUDE = 1.0
        const val SCISSORS_AMPLITUDE = 1.0
    }
}
