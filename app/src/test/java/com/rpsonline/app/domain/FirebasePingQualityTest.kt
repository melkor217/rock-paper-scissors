package com.rpsonline.app.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class FirebasePingQualityTest {
    @Test
    fun barCountFromLatency() {
        assertEquals(4, firebasePingBarCount(50))
        assertEquals(4, firebasePingBarCount(119))
        assertEquals(3, firebasePingBarCount(120))
        assertEquals(3, firebasePingBarCount(249))
        assertEquals(2, firebasePingBarCount(250))
        assertEquals(2, firebasePingBarCount(499))
        assertEquals(1, firebasePingBarCount(500))
        assertEquals(1, firebasePingBarCount(2000))
    }
}
