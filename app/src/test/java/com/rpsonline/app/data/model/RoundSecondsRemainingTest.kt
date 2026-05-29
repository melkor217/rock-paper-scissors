package com.rpsonline.app.data.model

import com.rpsonline.app.domain.GameRules
import org.junit.Assert.assertEquals
import org.junit.Test

class RoundSecondsRemainingTest {

    @Test
    fun roundSecondsRemaining_usesStartedAtAndCapsAtRoundTimeout() {
        val startMs = 1_000L
        val round = RoundResult(
            roundNumber = 1,
            startedAt = startMs,
            deadline = startMs + 120_000L,
        )

        assertEquals(
            GameRules.ROUND_TIMEOUT_SECONDS,
            round.roundSecondsRemaining(nowMs = startMs - 1_000L),
        )
        assertEquals(46, round.roundSecondsRemaining(nowMs = startMs + 14_000L))
        assertEquals(0, round.roundSecondsRemaining(nowMs = startMs + 60_000L))
    }

    @Test
    fun roundSecondsRemaining_derivesStartFromDeadlineWhenMissingStartedAt() {
        val deadlineMs = 61_000L
        val round = RoundResult(roundNumber = 1, deadline = deadlineMs)

        assertEquals(60, round.roundSecondsRemaining(nowMs = 1_000L))
        assertEquals(30, round.roundSecondsRemaining(nowMs = 31_000L))
    }
}
