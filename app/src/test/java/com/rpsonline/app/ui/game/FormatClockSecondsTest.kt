package com.rpsonline.app.ui.game

import org.junit.Assert.assertEquals
import org.junit.Test

class FormatClockSecondsTest {

    @Test
    fun formatClockSeconds_rendersMinutesAndSeconds() {
        assertEquals("1:00", formatClockSeconds(60))
        assertEquals("0:05", formatClockSeconds(5))
        assertEquals("2:30", formatClockSeconds(150))
    }
}
