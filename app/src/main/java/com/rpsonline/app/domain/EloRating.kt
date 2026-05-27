package com.rpsonline.app.domain

import com.rpsonline.app.data.model.Match
import com.rpsonline.app.data.model.MatchHistoryEntry
import com.rpsonline.app.data.model.ViewerMatchResolution
import com.rpsonline.app.data.model.toHistoryEntry
import com.rpsonline.app.data.model.viewerResolution
import kotlin.math.log10
import kotlin.math.roundToInt

const val ELO_K_FACTOR = 32.0

fun inferOpponentPreMatchElo(
    myPreMatchElo: Int,
    myEloDelta: Int,
    myScore: Double,
): Int? {
    val expectedA = myScore - myEloDelta / ELO_K_FACTOR
    if (expectedA <= 0.0 || expectedA >= 1.0) return null
    val opponentPre = myPreMatchElo + 400.0 * log10(1.0 / expectedA - 1.0)
    return opponentPre.roundToInt().coerceAtLeast(0)
}

fun Match.myScore(userId: String): Double? =
    when (viewerResolution(userId)) {
        ViewerMatchResolution.WIN -> 1.0
        ViewerMatchResolution.LOSS -> 0.0
        ViewerMatchResolution.DRAW -> 0.5
        ViewerMatchResolution.ABANDONED, null -> null
    }

fun Match.opponentEloAtMatch(userId: String, myCurrentElo: Int): Int? {
    opponentElo(userId)?.let { return it }
    val myDelta = myEloDelta(userId) ?: return null
    val myScore = myScore(userId) ?: return null
    val myPre = myCurrentElo - myDelta
    return inferOpponentPreMatchElo(myPre, myDelta, myScore)
}

/** Newest-first [matches]; [myCurrentElo] is the viewer's rating after the newest listed match. */
fun enrichMatchHistoryWithOpponentElos(
    viewerId: String,
    myCurrentElo: Int,
    matches: List<Match>,
): List<MatchHistoryEntry> {
    var runningMyElo = myCurrentElo
    return matches.map { match ->
        val entry = match.toHistoryEntry(viewerId)
        val myDelta = match.myEloDelta(viewerId)
        val myPreMatchElo = when {
            entry.myElo != null -> entry.myElo
            myDelta != null -> runningMyElo - myDelta
            else -> null
        }
        val opponentElo = entry.opponentElo ?: run {
            if (myDelta == null || myPreMatchElo == null) return@run null
            val myScore = match.myScore(viewerId) ?: return@run null
            inferOpponentPreMatchElo(myPreMatchElo, myDelta, myScore)
        }
        if (myDelta != null) {
            runningMyElo -= myDelta
        }
        entry.copy(
            myElo = myPreMatchElo,
            opponentElo = opponentElo ?: entry.opponentElo,
        )
    }
}
