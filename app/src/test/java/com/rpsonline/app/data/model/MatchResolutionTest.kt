package com.rpsonline.app.data.model

import com.rpsonline.app.domain.MatchMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MatchResolutionTest {
    @Test
    fun `uses stored resolution when present`() {
        val match = completedMatch(resolution = MatchResolution.PLAYER2_WIN)
        assertEquals(MatchResolution.PLAYER2_WIN, match.resolvedOutcome())
        assertEquals(ViewerMatchResolution.WIN, match.viewerResolution("p2"))
        assertEquals(ViewerMatchResolution.LOSS, match.viewerResolution("p1"))
    }

    @Test
    fun `infers draw from legacy fields`() {
        val match = completedMatch(
            winnerId = null,
            player1Wins = 5,
            player2Wins = 5,
        )
        assertEquals(MatchResolution.DRAW, match.resolvedOutcome())
        assertEquals(ViewerMatchResolution.DRAW, match.viewerResolution("p1"))
    }

    @Test
    fun `infers abandoned from legacy status`() {
        val match = completedMatch(status = MatchStatus.ABANDONED)
        assertEquals(MatchResolution.ABANDONED, match.resolvedOutcome())
        assertEquals(ViewerMatchResolution.ABANDONED, match.viewerResolution("p1"))
    }

    @Test
    fun `active match has no resolution`() {
        val match = completedMatch(status = MatchStatus.ACTIVE)
        assertNull(match.resolvedOutcome())
        assertNull(match.viewerResolution("p1"))
    }

    @Test
    fun `history entry carries viewer resolution`() {
        val match = completedMatch(resolution = MatchResolution.PLAYER1_WIN)
        val entry = match.toHistoryEntry("p1")
        assertEquals(ViewerMatchResolution.WIN, entry.resolution)
    }

    private fun completedMatch(
        status: MatchStatus = MatchStatus.COMPLETED,
        winnerId: String? = "p1",
        player1Wins: Int = 2,
        player2Wins: Int = 1,
        resolution: MatchResolution? = null,
    ): Match = Match(
        id = "m1",
        player1 = "p1",
        player2 = "p2",
        matchMode = MatchMode.BO3,
        status = status,
        player1Wins = player1Wins,
        player2Wins = player2Wins,
        winnerId = winnerId,
        resolution = resolution,
    )
}
