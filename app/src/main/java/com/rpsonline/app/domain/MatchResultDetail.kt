package com.rpsonline.app.domain

import com.rpsonline.app.data.model.Match
import com.rpsonline.app.data.model.MatchEndReason
import com.rpsonline.app.data.model.MatchStatus

/**
 * Subtitle for the result screen explaining how the match was decided.
 * Returns null for normal wins/losses on points and for draws.
 */
fun matchResultOutcomeDetail(
    match: Match,
    won: Boolean,
    isDraw: Boolean,
): String? {
    if (isDraw || match.status != MatchStatus.COMPLETED) return null

    match.endReason?.let { reason ->
        return detailForReason(reason, won)
    }

    if (!match.endedByForfeitRound()) return null
    return if (won) {
        "Won on timeout"
    } else {
        "Lost on timeout"
    }
}

private fun detailForReason(reason: MatchEndReason, won: Boolean): String? = when (reason) {
    MatchEndReason.NORMAL -> null
    MatchEndReason.ROUND_TIMEOUT -> if (won) {
        "Won on round timeout — opponent didn't play in time"
    } else {
        "Lost on round timeout — you didn't play in time"
    }
    MatchEndReason.CLOCK_TIMEOUT -> if (won) {
        "Won on clock timeout — opponent's match clock ran out"
    } else {
        "Lost on clock timeout — your match clock ran out"
    }
}

/** Final decisive round had a forfeit (one player never locked a move). */
private fun Match.endedByForfeitRound(): Boolean {
    val last = rounds
        .filter { it.winner != null && it.winner != "tie" }
        .maxByOrNull { it.resolvedAt ?: 0L }
        ?: return false
    val p1Missing = last.player1Choice == null
    val p2Missing = last.player2Choice == null
    return p1Missing != p2Missing
}
