package com.rpsonline.app.ui.util

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.media.ToneGenerator
import android.media.AudioManager

fun playMatchFoundSound(context: Context) {
    try {
        val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
        if (uri != null) {
            MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build(),
                )
                setDataSource(context, uri)
                setOnCompletionListener { release() }
                setOnErrorListener { _, _, _ ->
                    release()
                    true
                }
                prepare()
                start()
            }
            return
        }
    } catch (_: Exception) {
        // Fall through to tone beep
    }

    try {
        val tone = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 80)
        tone.startTone(ToneGenerator.TONE_PROP_ACK, 200)
        tone.release()
    } catch (_: Exception) {
        // No audio available
    }
}
