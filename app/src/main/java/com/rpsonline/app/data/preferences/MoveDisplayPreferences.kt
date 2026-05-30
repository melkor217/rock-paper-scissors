package com.rpsonline.app.data.preferences

import android.content.Context

private const val PREFS_NAME = "rps_move_display_prefs"
private const val KEY_OWN_MOVE_REVEALED = "own_move_revealed"

class MoveDisplayPreferences(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** True when the player wants to see their own submitted move; false shows the hidden marker. */
    fun isOwnMoveRevealed(): Boolean = prefs.getBoolean(KEY_OWN_MOVE_REVEALED, false)

    fun setOwnMoveRevealed(revealed: Boolean) {
        prefs.edit().putBoolean(KEY_OWN_MOVE_REVEALED, revealed).commit()
    }
}
