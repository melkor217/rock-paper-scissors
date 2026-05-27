package com.rpsonline.app.domain

import com.rpsonline.app.data.model.MatchHistoryEntry
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

data class DailyEloDelta(
    val dayLabel: String,
    val epochDay: Long,
    val netDelta: Int,
    val matchCount: Int,
)

private val DayLabelFormat = DateTimeFormatter.ofPattern("M/d")

/** Inclusive rolling 7-day window ending today in [zoneId]. */
fun weeklyChartWindowStartDay(
    zoneId: ZoneId = ZoneId.systemDefault(),
    clock: Clock = Clock.system(zoneId),
): LocalDate = LocalDate.now(clock.withZone(zoneId)).minusDays(6)

fun weeklyChartWindowStartMs(
    zoneId: ZoneId = ZoneId.systemDefault(),
    clock: Clock = Clock.system(zoneId),
): Long = weeklyChartWindowStartDay(zoneId, clock)
    .atStartOfDay(zoneId)
    .toInstant()
    .toEpochMilli()

fun weeklyEloDailyDeltas(
    matches: List<MatchHistoryEntry>,
    zoneId: ZoneId = ZoneId.systemDefault(),
    clock: Clock = Clock.system(ZoneId.systemDefault()),
    locale: Locale = Locale.getDefault(),
): List<DailyEloDelta> {
    val today = LocalDate.now(clock.withZone(zoneId))
    val startDay = today.minusDays(6)
    val dayLabelFormat = DayLabelFormat.withLocale(locale)

    val buckets = (0..6).associate { offset ->
        val day = startDay.plusDays(offset.toLong())
        day.toEpochDay() to DailyEloDelta(
            dayLabel = day.format(dayLabelFormat),
            epochDay = day.toEpochDay(),
            netDelta = 0,
            matchCount = 0,
        )
    }.toMutableMap()

    for (match in matches) {
        val activityAt = match.lastActivityAt
        if (activityAt <= 0L) continue
        val matchDay = Instant.ofEpochMilli(activityAt).atZone(zoneId).toLocalDate()
        if (matchDay.isBefore(startDay) || matchDay.isAfter(today)) continue
        val existing = buckets.getValue(matchDay.toEpochDay())
        buckets[matchDay.toEpochDay()] = existing.copy(
            netDelta = existing.netDelta + (match.eloDelta ?: 0),
            matchCount = existing.matchCount + 1,
        )
    }

    return buckets.values.sortedBy { it.epochDay }
}

fun weeklyEloNetDelta(days: List<DailyEloDelta>): Int = days.sumOf { it.netDelta }

fun weeklyEloMatchCount(days: List<DailyEloDelta>): Int = days.sumOf { it.matchCount }
