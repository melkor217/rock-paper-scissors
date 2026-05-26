package com.rpsonline.app.data.preferences

import android.content.Context

private const val PREFS_NAME = "rps_theme_prefs"
private const val KEY_STYLE = "app_theme_style"

class ThemePreferences(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun get(): AppThemeStyle = AppThemeStyle.fromId(prefs.getString(KEY_STYLE, null))

    fun set(style: AppThemeStyle) {
        prefs.edit().putString(KEY_STYLE, style.id).apply()
    }
}
