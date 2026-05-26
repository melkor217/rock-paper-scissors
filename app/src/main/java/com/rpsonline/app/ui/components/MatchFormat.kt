package com.rpsonline.app.ui.components

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
