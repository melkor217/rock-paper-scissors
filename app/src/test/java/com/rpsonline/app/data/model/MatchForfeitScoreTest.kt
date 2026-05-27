package com.rpsonline.app.data.model

import org.junit.Assert.assertEquals
import org.junit.Test

class MatchForfeitScoreTest {

    @Test
    fun timeoutForfeit_usesResolvedRoundWins_notInflatedSeriesScore() {
        val match = Match(
            player1 = "me",
            player2 = "opp",
            matchMode = com.rpsonline.app.domain.MatchMode.BO10,
            status = MatchStatus.COMPLETED,
            player1Wins = 6,
            player2Wins = 0,
            winnerId = "me",
            endReason = MatchEndReason.ROUND_TIMEOUT,
            rounds = listOf(
                RoundResult(
                    roundNumber = 1,
                    player1Choice = "ROCK",
                    winner = "me",
                    resolvedAt = 1_000L,
                    endReason = RoundEndReason.ROUND_TIMEOUT,
                ),
            ),
        )

        assertEquals(1, match.myWins("me"))
        assertEquals(0, match.opponentWins("me"))
    }
}
