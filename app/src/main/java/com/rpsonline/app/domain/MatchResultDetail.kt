package com.rpsonline.app.domain

import com.rpsonline.app.data.model.Match
import com.rpsonline.app.data.model.MatchEndReason
import com.rpsonline.app.data.model.MatchStatus
import com.rpsonline.app.data.model.ViewerMatchResolution

/**
 * Subtitle for the result screen explaining how the match was decided.
 * Returns null for normal wins/losses on points and for draws.
 */
fun matchResultOutcomeDetail(
    match: Match,
    resolution: ViewerMatchResolution?,
): String? {
    if (resolution == ViewerMatchResolution.DRAW || match.status != MatchStatus.COMPLETED) return null

    match.endReason?.let { reason ->
        return detailForReason(reason)
    }

    if (!match.endedByForfeitRound()) return null
    return "Timeout"
}

private fun detailForReason(reason: MatchEndReason): String? = when (reason) {
    MatchEndReason.NORMAL -> null
    MatchEndReason.ROUND_TIMEOUT -> "Round timeout"
    MatchEndReason.CLOCK_TIMEOUT -> "Clock timeout"
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
