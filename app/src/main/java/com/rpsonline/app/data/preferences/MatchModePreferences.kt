package com.rpsonline.app.data.preferences

import android.content.Context
import com.rpsonline.app.domain.MatchMode

private const val PREFS_NAME = "rps_match_mode_prefs"
private const val KEY_MODES = "selected_match_modes"

class MatchModePreferences(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun get(): Set<MatchMode> = MatchMode.parseStoredNames(prefs.getStringSet(KEY_MODES, null))

    fun set(modes: Set<MatchMode>) {
        require(modes.isNotEmpty()) { "At least one match mode must be selected" }
        prefs.edit()
            .putStringSet(KEY_MODES, modes.map { it.name }.toSet())
            .apply()
    }
}
