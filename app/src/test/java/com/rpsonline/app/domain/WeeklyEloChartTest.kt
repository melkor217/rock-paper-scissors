package com.rpsonline.app.domain

import com.rpsonline.app.data.model.MatchHistoryEntry
import com.rpsonline.app.data.model.ViewerMatchResolution
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class WeeklyEloChartTest {
    private val zoneId = ZoneId.of("UTC")
    private val fixedClock = Clock.fixed(Instant.parse("2026-05-27T12:00:00Z"), zoneId)

    @Test
    fun weeklyEloDailyDeltas_bucketsLastSevenDays() {
        val today = LocalDate.of(2026, 5, 27)
        val matches = listOf(
            match(lastActivityAt = today.atStartOfDay(zoneId).toInstant().toEpochMilli(), delta = 18),
            match(lastActivityAt = today.minusDays(1).atStartOfDay(zoneId).toInstant().toEpochMilli(), delta = -12),
            match(lastActivityAt = today.minusDays(2).atStartOfDay(zoneId).toInstant().toEpochMilli(), delta = 0),
            match(lastActivityAt = today.minusDays(8).atStartOfDay(zoneId).toInstant().toEpochMilli(), delta = 50),
        )

        val days = weeklyEloDailyDeltas(
            matches = matches,
            zoneId = zoneId,
            clock = fixedClock,
            locale = java.util.Locale.US,
        )

        assertEquals(7, days.size)
        assertEquals("5/27", days.last().dayLabel)
        assertEquals(18, days.last().netDelta)
        assertEquals(1, days.last().matchCount)
        assertEquals(-12, days[days.lastIndex - 1].netDelta)
        assertEquals(0, days[days.lastIndex - 2].netDelta)
        assertEquals(0, days.first().netDelta)
    }

    @Test
    fun weeklyEloDailyDeltas_sumsMultipleMatchesOnSameDay() {
        val today = LocalDate.of(2026, 5, 27)
        val noon = today.atTime(12, 0).atZone(zoneId).toInstant().toEpochMilli()
        val matches = listOf(
            match(lastActivityAt = noon, delta = 10),
            match(lastActivityAt = noon + 3_600_000, delta = -4),
            match(lastActivityAt = noon + 7_200_000, delta = null),
        )

        val days = weeklyEloDailyDeltas(
            matches = matches,
            zoneId = zoneId,
            clock = fixedClock,
            locale = java.util.Locale.US,
        )

        assertEquals(6, days.last().netDelta)
        assertEquals(3, days.last().matchCount)
    }

    @Test
    fun weeklyEloNetDelta_sumsDailyValues() {
        val days = listOf(
            DailyEloDelta("Mon", 0, 10, 1),
            DailyEloDelta("Tue", 1, -3, 1),
            DailyEloDelta("Wed", 2, 0, 0),
        )
        assertEquals(7, weeklyEloNetDelta(days))
        assertEquals(2, weeklyEloMatchCount(days))
    }

    @Test
    fun weeklyChartWindowStartMs_isStartOfOldestDayInWindow() {
        val zoneId = ZoneId.of("UTC")
        val clock = Clock.fixed(Instant.parse("2026-05-27T15:30:00Z"), zoneId)
        val startMs = weeklyChartWindowStartMs(zoneId = zoneId, clock = clock)
        val expected = LocalDate.of(2026, 5, 21)
            .atStartOfDay(zoneId)
            .toInstant()
            .toEpochMilli()
        assertEquals(expected, startMs)
    }

    private fun match(lastActivityAt: Long, delta: Int?): MatchHistoryEntry =
        MatchHistoryEntry(
            matchId = "m-$lastActivityAt",
            myDisplayName = "Me",
            opponentName = "Them",
            myWins = 1,
            opponentWins = 0,
            resolution = ViewerMatchResolution.WIN,
            eloDelta = delta,
            lastActivityAt = lastActivityAt,
            recaps = emptyList(),
        )
}
