package com.rpsonline.app.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ResolvedRoundRecapsTest {

    @Test
    fun cancelledStubRound_showsBothElapsedTimes_notTimeout() {
        val match = Match(
            player1 = "me",
            player2 = "opp",
            status = MatchStatus.ABANDONED,
            rounds = listOf(
                RoundResult(
                    roundNumber = 1,
                    player1Choice = "ROCK",
                    player2Choice = "SCISSORS",
                    winner = "me",
                    resolvedAt = 1_000L,
                    player1MoveMs = 3_000,
                    player2MoveMs = 4_000,
                ),
                RoundResult(
                    roundNumber = 2,
                    resolvedAt = 62_000L,
                    startedAt = 2_000L,
                    winner = null,
                ),
            ),
        )

        val recaps = match.resolvedRoundRecaps("me")

        assertEquals(2, recaps.size)
        val stub = recaps[1]
        assertTrue(stub.isCancelled)
        assertFalse(stub.iTimedOut)
        assertFalse(stub.opponentTimedOut)
        assertEquals(60_000, stub.myMoveMs)
        assertEquals(60_000, stub.opponentMoveMs)
    }

    @Test
    fun cancelledStub_usesRecordedMoveMsWhenPresent() {
        val match = Match(
            player1 = "me",
            player2 = "opp",
            rounds = listOf(
                RoundResult(
                    roundNumber = 1,
                    resolvedAt = 10_000L,
                    startedAt = 0L,
                    winner = null,
                    player1MoveMs = 5_000,
                    player2MoveMs = 7_000,
                ),
            ),
        )

        val recaps = match.resolvedRoundRecaps("me")
        val stub = recaps.single()

        assertEquals(5_000, stub.myMoveMs)
        assertEquals(7_000, stub.opponentMoveMs)
    }
}
