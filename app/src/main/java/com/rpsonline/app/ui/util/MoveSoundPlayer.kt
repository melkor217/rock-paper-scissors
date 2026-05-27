package com.rpsonline.app.ui.util

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import com.rpsonline.app.R
import com.rpsonline.app.data.model.Move
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

class MoveSoundPlayer(context: Context) {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    fun play(move: Move, repetitions: Int) {
        val resId = when (move) {
            Move.ROCK -> R.raw.move_rock
            Move.PAPER -> R.raw.move_paper
            Move.SCISSORS -> R.raw.move_scissors
        }
        val count = repetitions.coerceIn(1, 3)
        scope.launch {
            repeat(count) { index ->
                playOnce(resId)
                if (index < count - 1) {
                    delay(BURST_GAP_MS)
                }
            }
        }
    }

    fun release() {
        // MediaPlayer instances are created per play and released on completion.
    }

    private suspend fun playOnce(resId: Int) {
        suspendCancellableCoroutine { continuation ->
            val player = MediaPlayer.create(appContext, resId)
            if (player == null) {
                continuation.resume(Unit)
                return@suspendCancellableCoroutine
            }
            player.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_GAME)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
            )
            continuation.invokeOnCancellation {
                runCatching {
                    if (player.isPlaying) player.stop()
                    player.release()
                }
            }
            player.setOnCompletionListener {
                player.release()
                if (continuation.isActive) {
                    continuation.resume(Unit)
                }
            }
            player.setOnErrorListener { mp, _, _ ->
                mp.release()
                if (continuation.isActive) {
                    continuation.resume(Unit)
                }
                true
            }
            player.start()
        }
    }

    private companion object {
        const val BURST_GAP_MS = 70L
    }
}
