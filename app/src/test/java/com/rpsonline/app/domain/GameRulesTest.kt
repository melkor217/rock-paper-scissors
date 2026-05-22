package com.rpsonline.app.domain

import com.rpsonline.app.data.model.Move
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GameRulesTest {
    @Test
    fun tieReturnsNull() {
        assertNull(GameRules.resolveRound(Move.ROCK, Move.ROCK))
    }

    @Test
    fun rockBeatsScissors() {
        assertEquals(Move.ROCK, GameRules.resolveRound(Move.ROCK, Move.SCISSORS))
        assertEquals(Move.ROCK, GameRules.resolveRound(Move.SCISSORS, Move.ROCK))
    }

    @Test
    fun paperBeatsRock() {
        assertEquals(Move.PAPER, GameRules.resolveRound(Move.PAPER, Move.ROCK))
    }

    @Test
    fun scissorsBeatPaper() {
        assertEquals(Move.SCISSORS, GameRules.resolveRound(Move.SCISSORS, Move.PAPER))
    }
}
