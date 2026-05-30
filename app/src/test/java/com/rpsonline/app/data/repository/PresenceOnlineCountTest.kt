package com.rpsonline.app.data.repository

import org.junit.Assert.assertEquals
import org.junit.Test

class PresenceOnlineCountTest {

    @Test
    fun countOnlineUids_countsFreshPresenceOnly() {
        val nowMs = 1_700_000_000_000L

        assertEquals(
            2,
            PresenceRepository.countOnlineUids(
                lastSeenByUid = mapOf(
                    "fresh" to nowMs - 30_000L,
                    "stale" to nowMs - 120_000L,
                ),
                nowMs = nowMs,
                selfUid = "me",
            ),
        )
    }

    @Test
    fun countOnlineUids_dropsPlayersAfterPresenceWindow() {
        val nowMs = 1_700_000_000_000L

        assertEquals(
            1,
            PresenceRepository.countOnlineUids(
                lastSeenByUid = mapOf(
                    "gone" to nowMs - PresenceRepository.ONLINE_PRESENCE_WINDOW_MS - 1_000L,
                ),
                nowMs = nowMs,
                selfUid = "me",
            ),
        )
    }
}
