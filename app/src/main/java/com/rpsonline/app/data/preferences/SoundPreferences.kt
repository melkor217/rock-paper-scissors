package com.rpsonline.app.data.preferences

import android.content.Context

private const val PREFS_NAME = "rps_sound_prefs"
private const val KEY_CLOCK_MUTED = "clock_muted"

class SoundPreferences(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun isClockMuted(): Boolean = prefs.getBoolean(KEY_CLOCK_MUTED, false)

    fun setClockMuted(muted: Boolean) {
        prefs.edit().putBoolean(KEY_CLOCK_MUTED, muted).apply()
    }
}
