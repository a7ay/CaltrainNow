package com.caltrainnow.core.model

/**
 * Combined result from a join query of StopTime + Trip + Route.
 * Returned by the data source when looking up departures.
 */
data class StopTimeWithTrip(
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
)
