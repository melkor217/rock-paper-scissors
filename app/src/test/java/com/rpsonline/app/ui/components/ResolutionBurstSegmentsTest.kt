package com.rpsonline.app.ui.components

import com.rpsonline.app.data.model.Move
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ResolutionBurstSegmentsTest {

    @Test
    fun eachMove_hasDistinctFillSequence() {
        val rock = resolutionBurstFillSequence(Move.ROCK)
        val paper = resolutionBurstFillSequence(Move.PAPER)
        val scissors = resolutionBurstFillSequence(Move.SCISSORS)

        assertNotEquals(rock, paper)
        assertNotEquals(paper, scissors)
        assertNotEquals(rock, scissors)
    }

    @Test
    fun eachStep_isCumulativeSuperset() {
        listOf(Move.ROCK, Move.PAPER, Move.SCISSORS).forEach { move ->
            val sequence = resolutionBurstFillSequence(move)
            for (index in 1 until sequence.size) {
                assertTrue(
                    "Step $index for $move should include prior segments",
                    sequence[index].containsAll(sequence[index - 1]),
                )
            }
        }
    }

    @Test
    fun peakLightsAllSegments_forEveryMove() {
        listOf(Move.ROCK, Move.PAPER, Move.SCISSORS).forEach { move ->
            assertEquals(
                setOf('a', 'b', 'c', 'd', 'e', 'f', 'g'),
                resolutionBurstSegmentsAtProgress(move, 1f),
            )
            assertEquals(
                setOf('a', 'b', 'c', 'd', 'e', 'f', 'g'),
                resolutionBurstFillSequence(move).last(),
            )
        }
    }

    @Test
    fun fillProgress_reachesFullDisplayBeforeFade() {
        listOf(Move.ROCK, Move.PAPER, Move.SCISSORS).forEach { move ->
            assertEquals(
                setOf('a', 'b', 'c', 'd', 'e', 'f', 'g'),
                resolutionBurstSegmentsAtProgress(move, 0.99f),
            )
        }
    }

    @Test
    fun burstExcludesProtectedFullLitSegments() {
        val protected = setOf('a', 'b', 'g')
        val burst = resolutionBurstSegmentsExcluding(Move.ROCK, 1f, protected)
        assertEquals(setOf('c', 'd', 'e', 'f'), burst)
    }
}
