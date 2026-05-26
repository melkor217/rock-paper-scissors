package com.rpsonline.app.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class MatchModeTest {

    @Test
    fun bo3_requiresTwoWins() {
        assertEquals(2, MatchMode.BO3.winsToFinish)
        assertEquals(3, MatchMode.BO3.bestOfRounds)
        assertEquals("Best of 3", MatchMode.BO3.label)
    }

    @Test
    fun bo5_requiresThreeWins() {
        assertEquals(3, MatchMode.BO5.winsToFinish)
        assertEquals(5, MatchMode.BO5.bestOfRounds)
        assertEquals("Best of 5", MatchMode.BO5.label)
    }

    @Test
    fun fromString_defaultsToBo3() {
        assertEquals(MatchMode.BO3, MatchMode.fromString(null))
        assertEquals(MatchMode.BO3, MatchMode.fromString("unknown"))
        assertEquals(MatchMode.BO5, MatchMode.fromString("BO5"))
    }

    @Test
    fun parseRouteArg_supportsMultipleModes() {
        assertEquals(setOf(MatchMode.BO3, MatchMode.BO5), MatchMode.parseRouteArg("BO3,BO5"))
        assertEquals(MatchMode.DEFAULT_SELECTION, MatchMode.parseRouteArg(null))
    }

    @Test
    fun encodeRouteArg_isStable() {
        assertEquals("BO3,BO5", MatchMode.encodeRouteArg(setOf(MatchMode.BO5, MatchMode.BO3)))
    }

    @Test
    fun toggleInSelection_deselectingOnlyMode_selectsTheOther() {
        assertEquals(
            setOf(MatchMode.BO5),
            MatchMode.toggleInSelection(setOf(MatchMode.BO3), MatchMode.BO3),
        )
        assertEquals(
            setOf(MatchMode.BO3),
            MatchMode.toggleInSelection(setOf(MatchMode.BO5), MatchMode.BO5),
        )
    }

    @Test
    fun toggleInSelection_canSelectBoth() {
        assertEquals(
            setOf(MatchMode.BO3, MatchMode.BO5),
            MatchMode.toggleInSelection(setOf(MatchMode.BO3), MatchMode.BO5),
        )
    }
}
