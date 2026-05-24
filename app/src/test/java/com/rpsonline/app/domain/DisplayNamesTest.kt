package com.rpsonline.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DisplayNamesTest {
    @Test
    fun guestNameUsesUidPrefix() {
        assertEquals("Guest abc123", DisplayNames.guestName("abc123XYZ"))
    }

    @Test
    fun resolveGenericNamesToGuest() {
        assertEquals("Guest deadbe", DisplayNames.resolve("Player", "deadbeef"))
        assertEquals("Guest deadbe", DisplayNames.resolve(null, "deadbeef"))
        assertEquals("Guest deadbe", DisplayNames.resolve("", "deadbeef"))
    }

    @Test
    fun resolveKeepsCustomNames() {
        assertEquals("Alice", DisplayNames.resolve("Alice", "deadbeef"))
    }

    @Test
    fun isGenericDetectsDefaults() {
        assertTrue(DisplayNames.isGeneric("Player"))
        assertTrue(DisplayNames.isGeneric(null))
        assertFalse(DisplayNames.isGeneric("Guest abc123"))
    }
}
