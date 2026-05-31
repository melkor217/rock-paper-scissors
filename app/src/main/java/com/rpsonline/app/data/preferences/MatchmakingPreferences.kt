package com.rpsonline.app.data.preferences

import android.content.Context

private const val PREFS_NAME = "rps_matchmaking_prefs"
private const val KEY_BACKGROUND_USAGE = "background_usage_enabled"
private const val KEY_MATCH_FOUND_NOTIFICATIONS = "match_found_notifications_enabled"

class MatchmakingPreferences(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun isBackgroundUsageEnabled(): Boolean = prefs.getBoolean(KEY_BACKGROUND_USAGE, false)

    fun setBackgroundUsageEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_BACKGROUND_USAGE, enabled).apply()
    }

    fun isMatchFoundNotificationsEnabled(): Boolean =
        prefs.getBoolean(KEY_MATCH_FOUND_NOTIFICATIONS, false)

    fun setMatchFoundNotificationsEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_MATCH_FOUND_NOTIFICATIONS, enabled).apply()
    }
}
