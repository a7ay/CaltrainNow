package com.caltrainnow.core.model

/**
 * Represents a route from GTFS routes.txt.
 * Caltrain has routes for Local, Limited, Express, etc.
 */
data class Route(
    val routeId: String,
    val routeShortName: String?,   // e.g., "Local", "Limited", "Express"
    val routeLongName: String?,
    val routeType: Int = 2         // 2 = Rail in GTFS spec
)
