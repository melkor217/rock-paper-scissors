package com.rpsonline.app.ui.components

import com.rpsonline.app.data.model.RoundResult
import com.rpsonline.app.data.model.recapMoveDisplayMs
import com.rpsonline.app.data.model.roundElapsedMsAtTimeout
import com.rpsonline.app.domain.GameRules
import org.junit.Assert.assertEquals
import org.junit.Test

class FormatRecapMoveMsTest {

    @Test
    fun formatRecapMoveMs_roundsToWholeSeconds() {
        assertEquals("—", formatRecapMoveMs(null))
        assertEquals("4s", formatRecapMoveMs(3_500))
        assertEquals("12s", formatRecapMoveMs(12_400))
        assertEquals("60s", formatRecapMoveMs(59_600))
    }

    @Test
    fun recapMoveDisplayMs_timeoutUsesRoundElapsed() {
        val round = RoundResult(
            roundNumber = 1,
            resolvedAt = 10_000L,
            startedAt = 0L,
        )
        assertEquals(10_000, roundElapsedMsAtTimeout(round))
        assertEquals(10_000, recapMoveDisplayMs(moveMs = null, timedOut = true, round = round))
    }

    @Test
    fun recapMoveDisplayMs_timeoutFallsBackToRoundLimit() {
        val round = RoundResult(roundNumber = 1)
        assertEquals(
            GameRules.ROUND_TIMEOUT_SECONDS * 1000,
            roundElapsedMsAtTimeout(round),
        )
    }
}
