package com.rpsonline.app.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class EloRatingTest {
    @Test
    fun inferOpponentPreMatchElo_reversesCalculateElo() {
        val myPre = 987
        val opponentPre = 1031
        val myDelta = 18
        val score = 1.0
        val inferred = inferOpponentPreMatchElo(myPre, myDelta, score)
        assertEquals(opponentPre, inferred)
    }

    @Test
    fun inferOpponentPreMatchElo_returnsNullForInvalidExpectedScore() {
        assertEquals(null, inferOpponentPreMatchElo(1000, 32, 1.0))
    }
}
