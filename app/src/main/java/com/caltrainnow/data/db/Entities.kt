package com.caltrainnow.data.db

import androidx.room.*
import com.caltrainnow.core.model.*

// ── Room Entities ──────────────────────────────────────────────────
// These mirror the core models but add Room annotations.
// Mapping functions convert between Room entities and core models.

@Entity(tableName = "stations")
data class StationEntity(
    @PrimaryKey val stationId: String,
    val stationName: String,
    val latitude: Double,
    val longitude: Double,
    val parentId: String?,
    val locationType: Int
) {
    fun toModel() = Station(stationId, stationName, latitude, longitude, parentId, locationType)

    companion object {
        fun fromModel(m: Station) = StationEntity(
            m.stationId, m.stationName, m.latitude, m.longitude, m.parentId, m.locationType
        )
    }
}

@Entity(tableName = "trips")
data class TripEntity(
    @PrimaryKey val tripId: String,
    val routeId: String,
    val serviceId: String,
    val direction: Int,
    val tripHeadsign: String?
) {
    fun toModel() = Trip(tripId, routeId, serviceId, direction, tripHeadsign)

    companion object {
        fun fromModel(m: Trip) = TripEntity(
            m.tripId, m.routeId, m.serviceId, m.direction, m.tripHeadsign
        )
    }
}

@Entity(
    tableName = "stop_times",
    indices = [
        Index(value = ["tripId"]),
        Index(value = ["stationId"]),
        Index(value = ["departureTime"]),
        Index(value = ["stationId", "direction", "departureTime"])
    ],
    foreignKeys = [
        ForeignKey(
            entity = TripEntity::class,
            parentColumns = ["tripId"],
            childColumns = ["tripId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class StopTimeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val tripId: String,
    val stationId: String,
    val arrivalTime: String,
    val departureTime: String,
    val stopSequence: Int,
    // Denormalized for query performance — avoids JOIN on every lookup
    val direction: Int = 0,
    val serviceId: String = ""
) {
    fun toModel() = StopTime(id, tripId, stationId, arrivalTime, departureTime, stopSequence)

    companion object {
        fun fromModel(m: StopTime, direction: Int = 0, serviceId: String = "") = StopTimeEntity(
            m.id, m.tripId, m.stationId, m.arrivalTime, m.departureTime, m.stopSequence,
            direction, serviceId
        )
    }
}

@Entity(tableName = "routes")
data class RouteEntity(
    @PrimaryKey val routeId: String,
    val routeShortName: String?,
    val routeLongName: String?,
    val routeType: Int
) {
    fun toModel() = Route(routeId, routeShortName, routeLongName, routeType)

    companion object {
        fun fromModel(m: Route) = RouteEntity(
            m.routeId, m.routeShortName, m.routeLongName, m.routeType
        )
    }
}

@Entity(tableName = "service_calendars")
data class ServiceCalendarEntity(
    @PrimaryKey val serviceId: String,
    val monday: Boolean,
    val tuesday: Boolean,
    val wednesday: Boolean,
    val thursday: Boolean,
    val friday: Boolean,
    val saturday: Boolean,
    val sunday: Boolean,
    val startDate: String,
    val endDate: String
) {
    fun toModel() = ServiceCalendar(
        serviceId, monday, tuesday, wednesday, thursday, friday, saturday, sunday, startDate, endDate
    )

    companion object {
        fun fromModel(m: ServiceCalendar) = ServiceCalendarEntity(
            m.serviceId, m.monday, m.tuesday, m.wednesday, m.thursday,
            m.friday, m.saturday, m.sunday, m.startDate, m.endDate
        )
    }
}

@Entity(tableName = "service_exceptions")
data class ServiceExceptionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val serviceId: String,
    val date: String,
    val exceptionType: Int
) {
    fun toModel() = ServiceException(id, serviceId, date, exceptionType)

    companion object {
        fun fromModel(m: ServiceException) = ServiceExceptionEntity(
            m.id, m.serviceId, m.date, m.exceptionType
        )
    }
}

@Entity(tableName = "schedule_metadata")
data class ScheduleMetadataEntity(
    @PrimaryKey val id: Int = 1,
    val downloadedAt: String,
    val gtfsUrl: String,
    val stationCount: Int,
    val tripCount: Int,
    val stopTimeCount: Int
) {
    fun toModel() = ScheduleMetadata(id, downloadedAt, gtfsUrl, stationCount, tripCount, stopTimeCount)

    companion object {
        fun fromModel(m: ScheduleMetadata) = ScheduleMetadataEntity(
            m.id, m.downloadedAt, m.gtfsUrl, m.stationCount, m.tripCount, m.stopTimeCount
        )
    }
}

// ── Query result POJOs ─────────────────────────────────────────────

/**
 * POJO for the joined stop_times + trips + routes query.
 */
data class StopTimeWithTripPojo(
    val tripId: String,
    val stationId: String,
    val arrivalTime: String,
    val departureTime: String,
    val stopSequence: Int,
    val direction: Int,
    val tripHeadsign: String?,
    val routeId: String,
    val routeShortName: String?,
    val serviceId: String
) {
    fun toModel() = com.caltrainnow.core.model.StopTimeWithTrip(
        tripId, stationId, arrivalTime, departureTime, stopSequence,
        direction, tripHeadsign, routeId, routeShortName, serviceId
    )
}
