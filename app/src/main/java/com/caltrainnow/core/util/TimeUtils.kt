package com.caltrainnow.core.util

import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/**
 * Represents a GTFS time value.
 * GTFS times can exceed 24:00:00 for trips that extend past midnight
 * on the same service day (e.g., 25:30:00 = 1:30 AM next day).
 * We store as total minutes from midnight for easy comparison.
 */
data class GtfsTime(val totalMinutes: Int) : Comparable<GtfsTime> {

    val hours: Int get() = totalMinutes / 60
    val minutes: Int get() = totalMinutes % 60

    /**
     * Format for display. Normalizes to 12-hour clock.
     * 25:30 → "1:30 AM", 13:00 → "1:00 PM"
     */
    fun toDisplayString(): String {
        val normalizedHours = hours % 24
        val period = if (normalizedHours < 12) "AM" else "PM"
        val displayHour = when {
            normalizedHours == 0 -> 12
            normalizedHours > 12 -> normalizedHours - 12
            else -> normalizedHours
        }
        return "%d:%02d %s".format(displayHour, minutes, period)
    }

    /**
     * Format as HH:MM for compact display.
     */
    fun toTimeString(): String {
        val normalizedHours = hours % 24
        return "%d:%02d".format(normalizedHours, minutes)
    }

    override fun compareTo(other: GtfsTime): Int {
        return totalMinutes.compareTo(other.totalMinutes)
    }
}

/**
 * Time utility functions for GTFS schedule handling.
 * Pure Kotlin — no Android dependencies.
 */
object TimeUtils {

    /**
     * Parse a GTFS time string (HH:MM:SS) into a GtfsTime.
     * Supports hours >= 24 for after-midnight trips.
     */
    fun parseGtfsTime(timeStr: String): GtfsTime {
        val trimmed = timeStr.trim()
        val parts = trimmed.split(":")
        require(parts.size >= 2) { "Invalid GTFS time format: $timeStr" }

        val hours = parts[0].trim().toInt()
        val minutes = parts[1].trim().toInt()
        // Seconds are ignored for our purposes

        return GtfsTime(hours * 60 + minutes)
    }

    /**
     * Convert a LocalTime to GtfsTime.
     */
    fun fromLocalTime(localTime: LocalTime): GtfsTime {
        return GtfsTime(localTime.hour * 60 + localTime.minute)
    }

    /**
     * Get the current time as a GtfsTime.
     * If it's after midnight but before the end of GTFS service day (~3 AM),
     * we add 24 hours to match GTFS convention.
     */
    fun currentGtfsTime(localTime: LocalTime): GtfsTime {
        val baseMinutes = localTime.hour * 60 + localTime.minute
        // GTFS service day typically ends around 2-3 AM
        // If current time is between midnight and 3 AM, add 24h
        // to match GTFS times like 25:30:00
        return if (localTime.hour < 3) {
            GtfsTime(baseMinutes + 24 * 60)
        } else {
            GtfsTime(baseMinutes)
        }
    }

    /**
     * Calculate minutes until a departure.
     * Returns negative if the departure has already passed.
     */
    fun minutesUntil(current: GtfsTime, departure: GtfsTime): Long {
        return (departure.totalMinutes - current.totalMinutes).toLong()
    }

    /**
     * Check if a departure time is after the current time.
     */
    fun isDepartureAfter(current: GtfsTime, departure: GtfsTime): Boolean {
        return departure.totalMinutes > current.totalMinutes
    }

    /**
     * Format a GTFS time string for display.
     */
    fun formatForDisplay(gtfsTimeStr: String): String {
        return try {
            parseGtfsTime(gtfsTimeStr).toDisplayString()
        } catch (e: Exception) {
            gtfsTimeStr
        }
    }

    /**
     * Parse a YYYYMMDD date string to LocalDate.
     */
    fun parseGtfsDate(dateStr: String): LocalDate {
        val trimmed = dateStr.trim()
        return LocalDate.parse(trimmed, DateTimeFormatter.BASIC_ISO_DATE)
    }

    /**
     * Convert a GTFS time string to a sortable/comparable string.
     * Useful for SQL queries: departure_time > :currentTime
     */
    fun toSortableTimeString(gtfsTime: GtfsTime): String {
        return "%02d:%02d:%02d".format(gtfsTime.hours, gtfsTime.minutes, 0)
    }
}
