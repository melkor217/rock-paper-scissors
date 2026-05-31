package com.rpsonline.app.ui.util

import android.content.Context
import com.rpsonline.app.platform.AppForegroundTracker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** App-wide match clock tick loop; foreground UI and background service both call [sync]. */
object MatchClockSoundController {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val player = ClockTickPlayer()
    private var tickJob: Job? = null

    fun sync(shouldPlay: Boolean) {
        if (!shouldPlay) {
            tickJob?.cancel()
            tickJob = null
            player.stop()
            return
        }
        if (tickJob?.isActive == true) return
        tickJob = scope.launch {
            try {
                while (isActive) {
                    player.playTick()
                    delay(500)
                }
            } finally {
                player.stop()
            }
        }
    }

    /** Keeps match clock audible while backgrounded; Compose stops receiving match updates there. */
    fun syncFromSessionWhenBackground(context: Context) {
        if (AppForegroundTracker.isInForeground) return
        sync(MatchClockSoundPolicy.shouldPlayMatchClock(context))
    }
}
