package com.rpsonline.app.ui.util

import android.content.Context
import com.google.firebase.auth.FirebaseAuth
import com.rpsonline.app.data.model.MatchStatus
import com.rpsonline.app.data.preferences.SoundPreferences
import com.rpsonline.app.data.repository.MatchSessionMonitor

object MatchClockSoundPolicy {
    fun shouldPlayMatchClock(context: Context): Boolean {
        if (SoundPreferences(context).isClockMuted()) return false
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return false
        val match = MatchSessionMonitor.activeMatch.value ?: return false
        if (match.status != MatchStatus.ACTIVE || !match.isParticipant(uid)) return false
        return match.isPlayerClockRunning(uid)
    }
}
