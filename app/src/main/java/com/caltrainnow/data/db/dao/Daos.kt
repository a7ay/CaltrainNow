package com.caltrainnow.data.db.dao

import androidx.room.*
import com.caltrainnow.data.db.*

@Dao
interface StationDao {
    @Query("SELECT * FROM stations")
    suspend fun getAll(): List<StationEntity>

    @Query("SELECT * FROM stations WHERE stationId = :stationId")
    suspend fun getById(stationId: String): StationEntity?

    @Query("SELECT stationId FROM stations WHERE parentId = :parentId")
    suspend fun getChildStationIds(parentId: String): List<String>

    @Query("SELECT COUNT(*) FROM stations")
    suspend fun getCount(): Int

    @Query("SELECT COUNT(*) > 0 FROM stations WHERE stationName LIKE '%' || :namePattern || '%'")
    suspend fun existsByName(namePattern: String): Boolean

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(stations: List<StationEntity>)

    @Query("DELETE FROM stations")
    suspend fun deleteAll()
}

@Dao
interface TripDao {
    @Query("SELECT COUNT(*) FROM trips")
    suspend fun getCount(): Int

    @Query("SELECT COUNT(*) FROM trips WHERE direction = :direction")
    suspend fun getCountByDirection(direction: Int): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(trips: List<TripEntity>)

    @Query("DELETE FROM trips")
    suspend fun deleteAll()
}

@Dao
interface StopTimeDao {
    @Query("SELECT COUNT(*) FROM stop_times")
    suspend fun getCount(): Int

    /**
     * Get next departures from a station in a given direction.
     * Uses denormalized direction and serviceId columns for performance.
     */
    @Query("""
        SELECT 
            st.tripId,
            st.stationId,
            st.arrivalTime,
            st.departureTime,
            st.stopSequence,
            st.direction,
            t.tripHeadsign,
            t.routeId,
            r.routeShortName,
            st.serviceId
        FROM stop_times st
        JOIN trips t ON st.tripId = t.tripId
        LEFT JOIN routes r ON t.routeId = r.routeId
        WHERE st.stationId IN (:stationIds)
          AND st.direction = :direction
          AND st.serviceId IN (:serviceIds)
          AND st.departureTime > :afterTime
        ORDER BY st.departureTime ASC
        LIMIT :limit
    """)
    suspend fun getNextDepartures(
        stationIds: List<String>,
        direction: Int,
        serviceIds: List<String>,
        afterTime: String,
        limit: Int
    ): List<StopTimeWithTripPojo>

    /**
     * Get the arrival time for a specific trip at a specific station.
     */
    @Query("""
        SELECT 
            st.tripId,
            st.stationId,
            st.arrivalTime,
            st.departureTime,
            st.stopSequence,
            st.direction,
            t.tripHeadsign,
            t.routeId,
            r.routeShortName,
            st.serviceId
        FROM stop_times st
        JOIN trips t ON st.tripId = t.tripId
        LEFT JOIN routes r ON t.routeId = r.routeId
        WHERE st.tripId = :tripId
          AND st.stationId IN (:stationIds)
        LIMIT 1
    """)
    suspend fun getArrivalAtStation(tripId: String, stationIds: List<String>): StopTimeWithTripPojo?

    /**
     * Check for orphaned stop times (referencing non-existent trips).
     */
    @Query("""
        SELECT COUNT(*) > 0 FROM stop_times st 
        WHERE NOT EXISTS (SELECT 1 FROM trips t WHERE t.tripId = st.tripId)
    """)
    suspend fun hasOrphanedStopTimes(): Boolean

    /**
     * Check that stop sequences are valid (no duplicate sequence per trip).
     * Returns true if ALL sequences are valid.
     */
    @Query("""
        SELECT COUNT(*) = 0 FROM (
            SELECT tripId, stopSequence, COUNT(*) as cnt
            FROM stop_times
            GROUP BY tripId, stopSequence
            HAVING cnt > 1
        )
    """)
    suspend fun hasValidStopSequences(): Boolean

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(stopTimes: List<StopTimeEntity>)

    @Query("DELETE FROM stop_times")
    suspend fun deleteAll()
}

@Dao
interface RouteDao {
    @Query("SELECT * FROM routes")
    suspend fun getAll(): List<RouteEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(routes: List<RouteEntity>)

    @Query("DELETE FROM routes")
    suspend fun deleteAll()
}

@Dao
interface ServiceDao {
    @Query("SELECT * FROM service_calendars")
    suspend fun getAllCalendars(): List<ServiceCalendarEntity>

    @Query("SELECT * FROM service_exceptions")
    suspend fun getAllExceptions(): List<ServiceExceptionEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCalendars(calendars: List<ServiceCalendarEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExceptions(exceptions: List<ServiceExceptionEntity>)

    @Query("DELETE FROM service_calendars")
    suspend fun deleteAllCalendars()

    @Query("DELETE FROM service_exceptions")
    suspend fun deleteAllExceptions()
}

@Dao
interface MetadataDao {
    @Query("SELECT * FROM schedule_metadata WHERE id = 1")
    suspend fun get(): ScheduleMetadataEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(metadata: ScheduleMetadataEntity)

    @Query("DELETE FROM schedule_metadata")
    suspend fun delete()
}
