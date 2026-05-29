package com.rpsonline.app.ui.game

import com.rpsonline.app.domain.GameRules

/** Elapsed time in the current round when the server snapshot was received. */
internal fun roundElapsedAtSyncMs(
    roundStartMs: Long,
    wallNowMs: Long,
    timeoutSeconds: Int = GameRules.ROUND_TIMEOUT_SECONDS,
): Long {
    val timeoutMs = timeoutSeconds * 1_000L
    return (wallNowMs - roundStartMs).coerceIn(0, timeoutMs)
}

/** Round seconds remaining, ticking via monotonic elapsed time between server syncs. */
internal fun computeRoundSecondsFromAnchor(
    elapsedAtSyncMs: Long,
    syncElapsedRealtimeMs: Long,
    nowElapsedRealtimeMs: Long,
    timeoutSeconds: Int = GameRules.ROUND_TIMEOUT_SECONDS,
): Int {
    val timeoutMs = timeoutSeconds * 1_000L
    val monoElapsed = nowElapsedRealtimeMs - syncElapsedRealtimeMs
    val elapsedInRound = (elapsedAtSyncMs + monoElapsed).coerceIn(0, timeoutMs)
    val remainingMs = timeoutMs - elapsedInRound
    return ((remainingMs + 999) / 1_000).toInt().coerceIn(0, timeoutSeconds)
}
