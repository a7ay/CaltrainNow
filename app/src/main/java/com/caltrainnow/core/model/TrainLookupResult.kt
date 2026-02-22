package com.caltrainnow.core.model

/**
 * Result of a train lookup query.
 * Contains the nearest station, detected direction, and next departing trains.
 */
data class TrainLookupResult(
    val nearestStation: StationInfo,
    val direction: Direction,
    val directionReason: String,
    val nextTrains: List<TrainDeparture>,
    val stationDistanceMeters: Double
)

/**
 * Simplified station info for display.
 */
data class StationInfo(
    val stationId: String,
    val name: String,
    val latitude: Double,
    val longitude: Double
)

/**
 * A single upcoming train departure.
 */
data class TrainDeparture(
    val tripId: String,
    val departureTime: String,
    val arrivalTimeAtDestination: String?,
    val minutesUntilDeparture: Long,
    val headsign: String,
    val routeType: String,
    val trainNumber: String
)

/**
 * Train direction on the Caltrain corridor.
 */
enum class Direction(val gtfsValue: Int) {
    NORTHBOUND(0),    // Toward San Francisco
    SOUTHBOUND(1);    // Toward San Jose / Gilroy

    companion object {
        fun fromGtfs(value: Int): Direction =
            entries.first { it.gtfsValue == value }
    }
}
