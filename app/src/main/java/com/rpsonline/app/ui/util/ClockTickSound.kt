package com.rpsonline.app.ui.util

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

class ClockTickPlayer {
    private var audioTrack: AudioTrack? = null

    fun playTick() {
        try {
            val track = audioTrack ?: createAudioTrack().also { audioTrack = it }
            if (track.state != AudioTrack.STATE_INITIALIZED) {
                release()
                return
            }
            track.stop()
            track.reloadStaticData()
            track.play()
        } catch (_: Exception) {
            release()
        }
    }

    fun stop() {
        try {
            audioTrack?.let { track ->
                if (track.state == AudioTrack.STATE_INITIALIZED &&
                    track.playState == AudioTrack.PLAYSTATE_PLAYING
                ) {
                    track.stop()
                }
            }
        } catch (_: Exception) {
            release()
        }
    }

    fun release() {
        audioTrack?.run {
            stop()
            release()
        }
        audioTrack = null
    }

    private fun createAudioTrack(): AudioTrack {
        val samples = tickSamples()
        return AudioTrack.Builder()
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
    }

    private fun tickSamples(): ShortArray {
        val numSamples = SAMPLE_RATE * TICK_DURATION_MS / 1000
        val samples = ShortArray(numSamples)
        for (i in 0 until numSamples) {
            val t = i.toDouble() / SAMPLE_RATE
            val attack = (t / ATTACK_SECONDS).coerceAtMost(1.0)
            val decay = exp(-(t - ATTACK_SECONDS).coerceAtLeast(0.0) * 280)
            val envelope = attack * decay
            val body = sin(2 * PI * 1650 * t) * 0.55 + sin(2 * PI * 820 * t) * 0.35
            val transient = sin(2 * PI * 3200 * t) * exp(-t * 520) * 0.45
            val value = ((body + transient) * envelope * 0.95 * Short.MAX_VALUE).toInt()
            samples[i] = value.coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }
        return samples
    }

    private companion object {
        const val SAMPLE_RATE = 44_100
        const val TICK_DURATION_MS = 28
        const val ATTACK_SECONDS = 0.0015
    }
}
