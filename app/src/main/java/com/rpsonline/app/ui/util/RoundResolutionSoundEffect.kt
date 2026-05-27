package com.rpsonline.app.ui.util

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.rpsonline.app.data.model.Match
import com.rpsonline.app.data.model.Move
import com.rpsonline.app.data.model.RoundResult
import com.rpsonline.app.ui.LocalClockSoundMuted

fun roundResolutionRepetitions(resolved: RoundResult, userId: String): Int =
    when (resolved.winner) {
        "tie" -> 2
        userId -> 3
        else -> 1
    }

/**
 * Plays move sounds when a round resolves on the active match listener.
 */
@Composable
fun RoundResolutionSoundEffect(
    activeMatch: Match?,
    userId: String?,
) {
    val context = LocalContext.current
    val muted = LocalClockSoundMuted.current
    val moveSoundPlayer = remember(context) { MoveSoundPlayer(context) }

    var baselineInitialized by remember(userId) { mutableStateOf(false) }
    var lastPlayedRoundKey by remember(userId) { mutableStateOf<String?>(null) }

    DisposableEffect(Unit) {
        onDispose { moveSoundPlayer.release() }
    }

    LaunchedEffect(userId) {
        baselineInitialized = false
        lastPlayedRoundKey = null
    }

    LaunchedEffect(activeMatch, userId, muted) {
        if (userId == null || muted) return@LaunchedEffect

        val match = activeMatch
        val resolved = match?.lastResolvedRound() ?: run {
            if (!baselineInitialized) baselineInitialized = true
            return@LaunchedEffect
        }
        if (resolved.player1Choice == null || resolved.player2Choice == null) {
            if (!baselineInitialized) baselineInitialized = true
            return@LaunchedEffect
        }

        val key = "${match.id}:${resolved.roundNumber}:${resolved.resolvedAt}"
        if (!baselineInitialized) {
            baselineInitialized = true
            lastPlayedRoundKey = key
            return@LaunchedEffect
        }
        if (key == lastPlayedRoundKey) return@LaunchedEffect

        lastPlayedRoundKey = key
        val myChoice = if (userId == match.player1) resolved.player1Choice else resolved.player2Choice
        val move = Move.fromString(myChoice) ?: return@LaunchedEffect
        moveSoundPlayer.play(move, roundResolutionRepetitions(resolved, userId))
    }
}
