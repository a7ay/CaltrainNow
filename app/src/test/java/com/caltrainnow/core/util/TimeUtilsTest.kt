package com.caltrainnow.core.util

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

class TimeUtilsTest {

    // ── parseGtfsTime ──────────────────────────────────────────────

    @Test
    fun `parseGtfsTime - normal time - returns correct value`() {
        val time = TimeUtils.parseGtfsTime("08:30:00")
        assertThat(time.hours).isEqualTo(8)
        assertThat(time.minutes).isEqualTo(30)
        assertThat(time.totalMinutes).isEqualTo(510)
    }

    @Test
    fun `parseGtfsTime - after midnight - returns correct value`() {
        val time = TimeUtils.parseGtfsTime("25:30:00")
        assertThat(time.hours).isEqualTo(25)
        assertThat(time.minutes).isEqualTo(30)
        assertThat(time.totalMinutes).isEqualTo(1530)
    }

    @Test
    fun `parseGtfsTime - midnight exactly - returns correct value`() {
        val time = TimeUtils.parseGtfsTime("24:00:00")
        assertThat(time.hours).isEqualTo(24)
        assertThat(time.minutes).isEqualTo(0)
        assertThat(time.totalMinutes).isEqualTo(1440)
    }

    @Test
    fun `parseGtfsTime - with leading spaces - handles correctly`() {
        val time = TimeUtils.parseGtfsTime(" 8:30:00")
        assertThat(time.hours).isEqualTo(8)
        assertThat(time.minutes).isEqualTo(30)
    }

    // ── GtfsTime display ───────────────────────────────────────────

    @Test
    fun `GtfsTime toDisplayString - morning - shows AM`() {
        val time = TimeUtils.parseGtfsTime("08:30:00")
        assertThat(time.toDisplayString()).isEqualTo("8:30 AM")
    }

    @Test
    fun `GtfsTime toDisplayString - afternoon - shows PM`() {
        val time = TimeUtils.parseGtfsTime("14:15:00")
        assertThat(time.toDisplayString()).isEqualTo("2:15 PM")
    }

    @Test
    fun `GtfsTime toDisplayString - midnight - shows 12 AM`() {
        val time = TimeUtils.parseGtfsTime("00:00:00")
        assertThat(time.toDisplayString()).isEqualTo("12:00 AM")
    }

    @Test
    fun `GtfsTime toDisplayString - noon - shows 12 PM`() {
        val time = TimeUtils.parseGtfsTime("12:00:00")
        assertThat(time.toDisplayString()).isEqualTo("12:00 PM")
    }

    @Test
    fun `GtfsTime toDisplayString - after midnight GTFS - normalizes correctly`() {
        val time = TimeUtils.parseGtfsTime("25:30:00")
        assertThat(time.toDisplayString()).isEqualTo("1:30 AM")
    }

    // ── currentGtfsTime ────────────────────────────────────────────

    @Test
    fun `currentGtfsTime - normal daytime - returns same value`() {
        val localTime = LocalTime.of(8, 30)
        val gtfsTime = TimeUtils.currentGtfsTime(localTime)
        assertThat(gtfsTime.totalMinutes).isEqualTo(510)
    }

    @Test
    fun `currentGtfsTime - after midnight before 3am - adds 24 hours`() {
        val localTime = LocalTime.of(1, 30) // 1:30 AM
        val gtfsTime = TimeUtils.currentGtfsTime(localTime)
        // Should be 25:30 in GTFS terms (1530 minutes)
        assertThat(gtfsTime.totalMinutes).isEqualTo(1530)
    }

    @Test
    fun `currentGtfsTime - at 3am - does NOT add 24 hours`() {
        val localTime = LocalTime.of(3, 0)
        val gtfsTime = TimeUtils.currentGtfsTime(localTime)
        assertThat(gtfsTime.totalMinutes).isEqualTo(180)
    }

    // ── minutesUntil ───────────────────────────────────────────────

    @Test
    fun `minutesUntil - departure in future - returns positive`() {
        val current = TimeUtils.parseGtfsTime("08:00:00")
        val departure = TimeUtils.parseGtfsTime("08:22:00")
        assertThat(TimeUtils.minutesUntil(current, departure)).isEqualTo(22)
    }

    @Test
    fun `minutesUntil - departure in past - returns negative`() {
        val current = TimeUtils.parseGtfsTime("09:00:00")
        val departure = TimeUtils.parseGtfsTime("08:30:00")
        assertThat(TimeUtils.minutesUntil(current, departure)).isEqualTo(-30)
    }

    @Test
    fun `minutesUntil - after midnight departure - handles correctly`() {
        val current = TimeUtils.parseGtfsTime("23:50:00")
        val departure = TimeUtils.parseGtfsTime("25:30:00")
        assertThat(TimeUtils.minutesUntil(current, departure)).isEqualTo(100)
    }

    // ── isDepartureAfter ───────────────────────────────────────────

    @Test
    fun `isDepartureAfter - future departure - returns true`() {
        val current = TimeUtils.parseGtfsTime("08:00:00")
        val departure = TimeUtils.parseGtfsTime("08:15:00")
        assertThat(TimeUtils.isDepartureAfter(current, departure)).isTrue()
    }

    @Test
    fun `isDepartureAfter - past departure - returns false`() {
        val current = TimeUtils.parseGtfsTime("09:00:00")
        val departure = TimeUtils.parseGtfsTime("08:15:00")
        assertThat(TimeUtils.isDepartureAfter(current, departure)).isFalse()
    }

    @Test
    fun `isDepartureAfter - same time - returns false`() {
        val current = TimeUtils.parseGtfsTime("08:00:00")
        val departure = TimeUtils.parseGtfsTime("08:00:00")
        assertThat(TimeUtils.isDepartureAfter(current, departure)).isFalse()
    }

    // ── parseGtfsDate ──────────────────────────────────────────────

    @Test
    fun `parseGtfsDate - valid date - returns LocalDate`() {
        val date = TimeUtils.parseGtfsDate("20251225")
        assertThat(date).isEqualTo(LocalDate.of(2025, 12, 25))
    }

    // ── toSortableTimeString ───────────────────────────────────────

    @Test
    fun `toSortableTimeString - produces sortable format`() {
        val time = TimeUtils.parseGtfsTime("08:05:00")
        val str = TimeUtils.toSortableTimeString(time)
        assertThat(str).isEqualTo("08:05:00")
    }

    @Test
    fun `toSortableTimeString - after midnight - preserves high hours`() {
        val time = TimeUtils.parseGtfsTime("25:30:00")
        val str = TimeUtils.toSortableTimeString(time)
        assertThat(str).isEqualTo("25:30:00")
    }

    // ── GtfsTime comparison ────────────────────────────────────────

    @Test
    fun `GtfsTime - comparable - earlier is less than later`() {
        val earlier = TimeUtils.parseGtfsTime("08:00:00")
        val later = TimeUtils.parseGtfsTime("09:00:00")
        assertThat(earlier < later).isTrue()
    }

    @Test
    fun `GtfsTime - comparable - after midnight is greater than before`() {
        val before = TimeUtils.parseGtfsTime("23:00:00")
        val after = TimeUtils.parseGtfsTime("25:00:00")
        assertThat(after > before).isTrue()
    }
}
