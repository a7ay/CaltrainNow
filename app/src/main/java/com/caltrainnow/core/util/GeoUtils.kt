package com.caltrainnow.core.util

import com.caltrainnow.core.model.Station
import kotlin.math.*

/**
 * Geographic utility functions.
 * Pure Kotlin — no Android dependencies.
 */
object GeoUtils {

    private const val EARTH_RADIUS_METERS = 6_371_000.0
    private const val ONE_MILE_METERS = 1609.34

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
     * 
     * @param preferredStationIds Set of station IDs to prioritize (e.g. Home/Work).
     * @param biasThresholdMeters If a preferred station is within this distance 
     *        of the absolute nearest station, it will be chosen instead.
     */
    fun findNearestStation(
        lat: Double,
        lng: Double,
        stations: List<Station>,
        preferredStationIds: Set<String> = emptySet(),
        biasThresholdMeters: Double = ONE_MILE_METERS
    ): Station? {
        if (stations.isEmpty()) return null

        // Prefer parent stations to avoid matching individual platforms
        val parentStations = stations.filter { it.locationType == 1 }
        val candidates = parentStations.ifEmpty { stations }

        val absoluteNearest = candidates.minByOrNull { station ->
            haversineDistance(lat, lng, station.latitude, station.longitude)
        } ?: return null

        if (preferredStationIds.isEmpty()) return absoluteNearest

        val nearestDist = haversineDistance(lat, lng, absoluteNearest.latitude, absoluteNearest.longitude)

        // Check if any preferred station is within the bias threshold of the absolute nearest
        val bestPreferred = candidates
            .filter { it.stationId in preferredStationIds }
            .map { it to haversineDistance(lat, lng, it.latitude, it.longitude) }
            .filter { (_, dist) -> dist <= nearestDist + biasThresholdMeters }
            .minByOrNull { (_, dist) -> dist }

        return bestPreferred?.first ?: absoluteNearest
    }

    /**
     * Calculate distance from a point to a station in meters.
     */
    fun distanceToStation(lat: Double, lng: Double, station: Station): Double {
        return haversineDistance(lat, lng, station.latitude, station.longitude)
    }
}
