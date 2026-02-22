package com.caltrainnow.core.model

/**
 * Represents a stop time from GTFS stop_times.txt.
 * Each row is one train stopping at one station at a specific time.
 */
data class StopTime(
    val id: Long = 0,
    val tripId: String,
    val stationId: String,
    val arrivalTime: String,      // HH:MM:SS format (can exceed 24:00:00)
    val departureTime: String,    // HH:MM:SS format (can exceed 24:00:00)
    val stopSequence: Int
)
