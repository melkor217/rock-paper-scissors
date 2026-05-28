package com.rpsonline.app.ui.leaderboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WinRatePercentTest {

    @Test
    fun winRate_excludesDrawsFromDenominator() {
        assertEquals(50, winRatePercent(wins = 5, losses = 5, draws = 0))
        assertEquals(100, winRatePercent(wins = 5, losses = 0, draws = 5))
        assertEquals(50, winRatePercent(wins = 1, losses = 1, draws = 1))
    }

    @Test
    fun winRate_nullWhenNoGames() {
        assertNull(winRatePercent(0, 0, 0))
        assertNull(winRatePercent(0, 0, 7))
    }
}
