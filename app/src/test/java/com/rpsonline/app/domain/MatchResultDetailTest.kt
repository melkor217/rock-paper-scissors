package com.rpsonline.app.domain

import com.rpsonline.app.data.model.Match
import com.rpsonline.app.data.model.MatchEndReason
import com.rpsonline.app.data.model.MatchStatus
import com.rpsonline.app.data.model.RoundResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MatchResultDetailTest {

    private val me = "me"
    private val opp = "opp"

    @Test
    fun roundTimeout_win() {
        val match = completedMatch(endReason = MatchEndReason.ROUND_TIMEOUT)
        assertEquals(
            "Won on round timeout — opponent didn't play in time",
            matchResultOutcomeDetail(match, won = true, isDraw = false),
        )
    }

    @Test
    fun clockTimeout_loss() {
        val match = completedMatch(endReason = MatchEndReason.CLOCK_TIMEOUT, winnerId = opp)
        assertEquals(
            "Lost on clock timeout — your match clock ran out",
            matchResultOutcomeDetail(match, won = false, isDraw = false),
        )
    }

    @Test
    fun normalWin_noDetail() {
        val match = completedMatch(
            endReason = MatchEndReason.NORMAL,
            rounds = listOf(
                RoundResult(
                    roundNumber = 1,
                    player1Choice = "ROCK",
                    player2Choice = "SCISSORS",
                    winner = me,
                    resolvedAt = 1L,
                ),
            ),
        )
        assertNull(matchResultOutcomeDetail(match, won = true, isDraw = false))
    }

    @Test
    fun legacyForfeit_fallsBackToGenericTimeout() {
        val match = completedMatch(
            endReason = null,
            rounds = listOf(
                RoundResult(
                    roundNumber = 1,
                    player1Choice = "ROCK",
                    winner = me,
                    resolvedAt = 1L,
                ),
            ),
        )
        assertEquals(
            "Won on timeout",
            matchResultOutcomeDetail(match, won = true, isDraw = false),
        )
    }

    private fun completedMatch(
        endReason: MatchEndReason?,
        winnerId: String = me,
        rounds: List<RoundResult> = emptyList(),
    ) = Match(
        player1 = me,
        player2 = opp,
        status = MatchStatus.COMPLETED,
        winnerId = winnerId,
        endReason = endReason,
        rounds = rounds,
    )
}
