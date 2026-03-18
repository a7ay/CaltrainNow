package com.caltrainnow.core.datasource

import com.caltrainnow.core.model.*

/**
 * Abstraction over the schedule data store.
 *
 * All core logic depends on this interface, not on Room or any specific database.
 * This allows the core module to remain pure Kotlin and enables future portability
 * (e.g., Kotlin Multiplatform, server-side with PostgreSQL).
 *
 * Current implementation: RoomScheduleDataSource (Android Room/SQLite)
 */
interface ScheduleDataSource {

    // ── Read operations ────────────────────────────────────────────────

    /** Get all stations. Prefer parent stations for user-facing logic. */
    suspend fun getAllStations(): List<Station>

    /** Get a single station by ID. */
    suspend fun getStationById(stationId: String): Station?

    /** Get all child station IDs for a parent station. */
    suspend fun getChildStationIds(parentId: String): List<String>

    /** Get all routes. */
    suspend fun getAllRoutes(): List<Route>

    /** Get active service IDs for a given date, considering calendar + exceptions. */
    suspend fun getServiceCalendars(): List<ServiceCalendar>

    /** Get all service exceptions. */
    suspend fun getServiceExceptions(): List<ServiceException>

    /**
     * Get the next departures from a station in a given direction.
     * Only returns trips with active service IDs and departure after the given time.
     */
    suspend fun getNextDepartures(
        stationIds: List<String>,    // Station ID(s) — may include parent + children
        direction: Int,
        serviceIds: List<String>,
        afterTime: String,           // HH:MM:SS sortable format
        limit: Int = 4
    ): List<StopTimeWithTrip>

    /** Get the arrival time for a specific trip at a specific station. */
    suspend fun getArrivalAtStation(tripId: String, stationIds: List<String>): StopTimeWithTrip?

    // ── Counts for validation ──────────────────────────────────────────

    suspend fun getStationCount(): Int
    suspend fun getTripCount(): Int
    suspend fun getStopTimeCount(): Int
    suspend fun getTripCountByDirection(direction: Int): Int

    // ── Validation queries ─────────────────────────────────────────────

    /** Check if any StopTime references a non-existent trip or station. */
    suspend fun hasOrphanedStopTimes(): Boolean

    /** Check if stop_sequences are valid (ascending per trip). */
    suspend fun hasValidStopSequences(): Boolean

    /** Check if a station with the given name exists. */
    suspend fun stationExists(namePattern: String): Boolean

    // ── Write operations (for initialization) ──────────────────────────

    /** Delete all schedule data. */
    suspend fun clearAll()

    /** Bulk insert operations. */
    suspend fun insertStations(stations: List<Station>)
    suspend fun insertTrips(trips: List<Trip>)
    suspend fun insertStopTimes(stopTimes: List<StopTime>)
    suspend fun insertRoutes(routes: List<Route>)
    suspend fun insertServiceCalendars(calendars: List<ServiceCalendar>)
    suspend fun insertServiceExceptions(exceptions: List<ServiceException>)
    suspend fun insertMetadata(metadata: ScheduleMetadata)

    /** Get current schedule metadata, or null if no schedule loaded. */
    suspend fun getMetadata(): ScheduleMetadata?
}
