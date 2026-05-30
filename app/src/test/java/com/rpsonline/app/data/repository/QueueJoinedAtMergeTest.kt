package com.rpsonline.app.data.repository

import org.junit.Assert.assertEquals
import org.junit.Test

class QueueJoinedAtMergeTest {

    @Test
    fun merge_prefersMostRecentJoinTimestamp() {
        val stale = 1_000L
        val current = 10_000L
        assertEquals(10_000L, mergeQueueJoinedAtMs(current, stale))
    }

    @Test
    fun merge_acceptsNewerCandidate() {
        val current = 10_000L
        val serverConfirmed = 10_500L
        assertEquals(10_500L, mergeQueueJoinedAtMs(current, serverConfirmed))
    }

    @Test
    fun merge_usesCandidateWhenUnset() {
        assertEquals(42_000L, mergeQueueJoinedAtMs(null, 42_000L))
    }
}
