package com.rpsonline.app.ui.leaderboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ThrowsPerRoundTest {

    @Test
    fun throwsPerRound_usesRoundsWonAsDenominator() {
        assertEquals(4.0, throwsPerRound(3, 4, 4, 4)!!, 0.001)
        assertNull(throwsPerRound(0, 10, 10, 10))
    }

    @Test
    fun hasThrowStats_requiresRoundsWon() {
        assertEquals(false, hasThrowStats(0, 5, 5, 5))
        assertEquals(true, hasThrowStats(1, 5, 5, 5))
    }
}
