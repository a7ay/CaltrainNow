package com.caltrainnow.core.model

/**
 * Singleton metadata about the currently loaded schedule.
 * Tracks when data was last downloaded and basic counts for validation.
 */
data class ScheduleMetadata(
    val id: Int = 1,               // Always 1 — singleton row
    val downloadedAt: String,      // ISO 8601 timestamp
    val gtfsUrl: String,
    val stationCount: Int,
    val tripCount: Int,
    val stopTimeCount: Int
)
