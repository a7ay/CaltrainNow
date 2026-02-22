package com.caltrainnow.core.util

import com.caltrainnow.core.model.Station
import kotlin.math.*

/**
 * Geographic utility functions.
 * Pure Kotlin — no Android dependencies.
 */
object GeoUtils {

    private const val EARTH_RADIUS_METERS = 6_371_000.0

    /**
     * Calculate the haversine distance between two points in meters.
     */
    fun haversineDistance(
        lat1: Double, lng1: Double,
        lat2: Double, lng2: Double
    ): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLng = Math.toRadians(lng2 - lng1)
        val a = sin(dLat / 2).pow(2) +
                cos(Math.toRadians(lat1)) *
                cos(Math.toRadians(lat2)) *
                sin(dLng / 2).pow(2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return EARTH_RADIUS_METERS * c
    }

    /**
     * Find the nearest station to the given coordinates.
     * Returns null if the station list is empty.
     * Only considers parent stations (locationType == 1) if available,
     * otherwise falls back to all stations.
     */
    fun findNearestStation(
        lat: Double,
        lng: Double,
        stations: List<Station>
    ): Station? {
        if (stations.isEmpty()) return null

        // Prefer parent stations to avoid matching individual platforms
        val parentStations = stations.filter { it.locationType == 1 }
        val candidates = parentStations.ifEmpty { stations }

        return candidates.minByOrNull { station ->
            haversineDistance(lat, lng, station.latitude, station.longitude)
        }
    }

    /**
     * Calculate distance from a point to a station in meters.
     */
    fun distanceToStation(lat: Double, lng: Double, station: Station): Double {
        return haversineDistance(lat, lng, station.latitude, station.longitude)
    }
}
