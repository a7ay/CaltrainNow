package com.caltrainnow.core.model

/**
 * Represents a Caltrain trip parsed from GTFS trips.txt.
 * A trip is a single run of a train (e.g., Train #507 northbound).
 */
data class Trip(
    val tripId: String,
    val routeId: String,
    val serviceId: String,
    val direction: Int,           // 0 = Northbound, 1 = Southbound
    val tripHeadsign: String? = null  // e.g., "San Francisco" or "San Jose Diridon"
)
