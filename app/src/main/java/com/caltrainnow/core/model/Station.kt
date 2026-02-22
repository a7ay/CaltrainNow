package com.caltrainnow.core.model

/**
 * Represents a Caltrain station parsed from GTFS stops.txt.
 * Used as both a domain model and a Room entity (via Room annotations in the data layer).
 */
data class Station(
    val stationId: String,
    val stationName: String,
    val latitude: Double,
    val longitude: Double,
    val parentId: String? = null,
    val locationType: Int = 0  // 0 = stop/platform, 1 = station (parent)
)
