package com.caltrainnow.data.db

import androidx.room.withTransaction
import com.caltrainnow.core.datasource.ScheduleDataSource
import com.caltrainnow.core.model.*
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Room/SQLite implementation of ScheduleDataSource.
 * Bridges the core Kotlin interface to Android Room DAOs.
 */
@Singleton
class RoomScheduleDataSource @Inject constructor(
    private val db: CaltrainDatabase
) : ScheduleDataSource {

    // ── Read operations ────────────────────────────────────────────

    override suspend fun getAllStations(): List<Station> {
        return db.stationDao().getAll().map { it.toModel() }
    }

    override suspend fun getStationById(stationId: String): Station? {
        return db.stationDao().getById(stationId)?.toModel()
    }

    override suspend fun getChildStationIds(parentId: String): List<String> {
        return db.stationDao().getChildStationIds(parentId)
    }

    override suspend fun getAllRoutes(): List<Route> {
        return db.routeDao().getAll().map { it.toModel() }
    }

    override suspend fun getServiceCalendars(): List<ServiceCalendar> {
        return db.serviceDao().getAllCalendars().map { it.toModel() }
    }

    override suspend fun getServiceExceptions(): List<ServiceException> {
        return db.serviceDao().getAllExceptions().map { it.toModel() }
    }

    override suspend fun getNextDepartures(
        stationIds: List<String>,
        direction: Int,
        serviceIds: List<String>,
        afterTime: String,
        limit: Int
    ): List<StopTimeWithTrip> {
        return db.stopTimeDao().getNextDepartures(
            stationIds, direction, serviceIds, afterTime, limit
        ).map { it.toModel() }
    }

    override suspend fun getArrivalAtStation(
        tripId: String,
        stationIds: List<String>
    ): StopTimeWithTrip? {
        return db.stopTimeDao().getArrivalAtStation(tripId, stationIds)?.toModel()
    }

    // ── Counts ─────────────────────────────────────────────────────

    override suspend fun getStationCount(): Int = db.stationDao().getCount()
    override suspend fun getTripCount(): Int = db.tripDao().getCount()
    override suspend fun getStopTimeCount(): Int = db.stopTimeDao().getCount()
    override suspend fun getTripCountByDirection(direction: Int): Int =
        db.tripDao().getCountByDirection(direction)

    // ── Validation ─────────────────────────────────────────────────

    override suspend fun hasOrphanedStopTimes(): Boolean =
        db.stopTimeDao().hasOrphanedStopTimes()

    override suspend fun hasValidStopSequences(): Boolean =
        db.stopTimeDao().hasValidStopSequences()

    override suspend fun stationExists(namePattern: String): Boolean =
        db.stationDao().existsByName(namePattern)

    // ── Write operations ───────────────────────────────────────────

    override suspend fun clearAll() {
        db.withTransaction {
            db.metadataDao().delete()
            db.stopTimeDao().deleteAll()
            db.tripDao().deleteAll()
            db.stationDao().deleteAll()
            db.routeDao().deleteAll()
            db.serviceDao().deleteAllCalendars()
            db.serviceDao().deleteAllExceptions()
        }
    }

    override suspend fun insertStations(stations: List<Station>) {
        db.stationDao().insertAll(stations.map { StationEntity.fromModel(it) })
    }

    override suspend fun insertTrips(trips: List<Trip>) {
        db.tripDao().insertAll(trips.map { TripEntity.fromModel(it) })
    }

    override suspend fun insertStopTimes(stopTimes: List<StopTime>) {
        // Insert in batches to avoid SQLite variable limit
        stopTimes.chunked(500).forEach { batch ->
            db.stopTimeDao().insertAll(batch.map { StopTimeEntity.fromModel(it) })
        }
    }

    /**
     * Insert stop times with denormalized direction and serviceId.
     * This is the preferred method — avoids JOINs during lookups.
     */
    suspend fun insertStopTimesWithTripInfo(
        stopTimes: List<StopTime>,
        tripLookup: Map<String, Trip>
    ) {
        val entities = stopTimes.map { st ->
            val trip = tripLookup[st.tripId]
            StopTimeEntity.fromModel(
                st,
                direction = trip?.direction ?: 0,
                serviceId = trip?.serviceId ?: ""
            )
        }
        entities.chunked(500).forEach { batch ->
            db.stopTimeDao().insertAll(batch)
        }
    }

    override suspend fun insertRoutes(routes: List<Route>) {
        db.routeDao().insertAll(routes.map { RouteEntity.fromModel(it) })
    }

    override suspend fun insertServiceCalendars(calendars: List<ServiceCalendar>) {
        db.serviceDao().insertCalendars(calendars.map { ServiceCalendarEntity.fromModel(it) })
    }

    override suspend fun insertServiceExceptions(exceptions: List<ServiceException>) {
        db.serviceDao().insertExceptions(exceptions.map { ServiceExceptionEntity.fromModel(it) })
    }

    override suspend fun insertMetadata(metadata: ScheduleMetadata) {
        db.metadataDao().insert(ScheduleMetadataEntity.fromModel(metadata))
    }

    override suspend fun getMetadata(): ScheduleMetadata? {
        return db.metadataDao().get()?.toModel()
    }
}
