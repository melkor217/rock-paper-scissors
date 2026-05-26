package com.rpsonline.app.ui.components

import com.rpsonline.app.domain.MatchMode

/** Shared match score format (e.g. `2–1`). */
fun formatMatchScore(myWins: Int, opponentWins: Int): String = "$myWins–$opponentWins"

fun formatEloDelta(delta: Int): String =
    if (delta >= 0) "+$delta" else "$delta"

fun formatMatchResultLine(
    outcomeLabel: String,
    myWins: Int,
    opponentWins: Int,
    eloDelta: Int?,
): String = buildString {
    append(outcomeLabel)
    append(' ')
    append(formatMatchScore(myWins, opponentWins))
    if (eloDelta != null) {
        append(" (${formatEloDelta(eloDelta)})")
    }
}

fun postMatchElo(preMatchElo: Int?, eloDelta: Int?): Int? {
    if (preMatchElo == null || eloDelta == null) return null
    return preMatchElo + eloDelta
}

fun formatMatchMode(mode: MatchMode): String = mode.label

/** e.g. `Best of 5 (first to 3)` or `Best of 10 (first to 6, 5–5 draw)`. */
fun formatMatchSeriesDetail(mode: MatchMode): String {
    val base = "${mode.label} (first to ${mode.winsToFinish})"
    val tied = mode.tiedSeriesScore
    return if (tied != null) {
        "$base, $tied–$tied draw"
    } else {
        base
    }
}

fun formatMatchModes(modes: Set<MatchMode>): String =
    modes.sortedBy { it.ordinal }.joinToString(" / ") { it.label }
