package com.rpsonline.app.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class AppVersionTest {
    @Test
    fun versionCodeFromTag_matchesCiScheme() {
        assertEquals(502, versionCodeFromTag("v0.5.2"))
        assertEquals(10000, versionCodeFromTag("v1.0.0"))
        assertEquals(0, versionCodeFromTag("v0.0.0"))
    }
}
