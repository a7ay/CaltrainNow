package com.caltrainnow.core.engine

import com.caltrainnow.core.model.Direction
import com.caltrainnow.core.model.Station
import com.caltrainnow.core.model.UserConfig
import com.caltrainnow.core.util.GeoUtils

/**
 * Determines travel direction (northbound/southbound) based on
 * the user's current location relative to their home and work.
 *
 * Logic:
 * - If closer to home → user is heading to work
 * - If closer to work → user is heading home
 * - Direction is determined by relative latitude of home vs work stations
 *   (Caltrain runs roughly north-south)
 *
 * Pure Kotlin — no Android dependencies.
 */
class DirectionResolver(var userConfig: UserConfig) {

    data class DirectionResult(
        val direction: Direction,
        val reason: String,
        val destinationStationId: String?
    )

    /**
     * Resolve the travel direction based on current location.
     *
     * @param currentLat Current latitude
     * @param currentLng Current longitude
     * @param stations All stations (used to find nearest to home/work)
     * @return DirectionResult with direction, reason, and destination station
     */
    fun resolve(
        currentLat: Double,
        currentLng: Double,
        stations: List<Station>
    ): DirectionResult {
        val distanceToHome = GeoUtils.haversineDistance(
            currentLat, currentLng,
            userConfig.homeLatitude, userConfig.homeLongitude
        )
        val distanceToWork = GeoUtils.haversineDistance(
            currentLat, currentLng,
            userConfig.workLatitude, userConfig.workLongitude
        )

        val isNearHome = distanceToHome <= distanceToWork

        // Find stations nearest to home and work
        val homeStation = GeoUtils.findNearestStation(
            userConfig.homeLatitude, userConfig.homeLongitude, stations
        )
        val workStation = GeoUtils.findNearestStation(
            userConfig.workLatitude, userConfig.workLongitude, stations
        )

        if (homeStation == null || workStation == null) {
            // Fallback: default to northbound if we can't resolve
            return DirectionResult(
                direction = Direction.NORTHBOUND,
                reason = "Could not determine direction — defaulting to Northbound",
                destinationStationId = null
            )
        }

        // Caltrain runs north (SF) to south (SJ/Gilroy)
        // Higher latitude = more north = closer to SF
        val homeIsNorth = homeStation.latitude > workStation.latitude

        return if (isNearHome) {
            // Heading to work
            val direction = if (homeIsNorth) Direction.SOUTHBOUND else Direction.NORTHBOUND
            DirectionResult(
                direction = direction,
                reason = "Near home (${userConfig.homeLabel}) → heading to work (${userConfig.workLabel})",
                destinationStationId = workStation.stationId
            )
        } else {
            // Heading home
            val direction = if (homeIsNorth) Direction.NORTHBOUND else Direction.SOUTHBOUND
            DirectionResult(
                direction = direction,
                reason = "Near work (${userConfig.workLabel}) → heading home (${userConfig.homeLabel})",
                destinationStationId = homeStation.stationId
            )
        }
    }
}
