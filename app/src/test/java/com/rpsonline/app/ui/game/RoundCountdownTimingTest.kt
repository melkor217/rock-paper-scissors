package com.rpsonline.app.ui.game

import org.junit.Assert.assertEquals
import org.junit.Test

class RoundCountdownTimingTest {

    @Test
    fun computeRoundSecondsFromAnchor_ticksDownWithMonotonicElapsed() {
        assertEquals(
            59,
            computeRoundSecondsFromAnchor(
                elapsedAtSyncMs = 0L,
                syncElapsedRealtimeMs = 1_000L,
                nowElapsedRealtimeMs = 2_000L,
            ),
        )
        assertEquals(
            0,
            computeRoundSecondsFromAnchor(
                elapsedAtSyncMs = 59_500L,
                syncElapsedRealtimeMs = 0L,
                nowElapsedRealtimeMs = 1_000L,
            ),
        )
    }

    @Test
    fun roundElapsedAtSyncMs_clampsWhenDeviceClockIsBehindServer() {
        assertEquals(
            0L,
            roundElapsedAtSyncMs(roundStartMs = 10_000L, wallNowMs = 9_000L),
        )
    }
}
