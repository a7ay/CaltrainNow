package com.caltrainnow.core.engine

import com.caltrainnow.core.datasource.ScheduleDataSource
import com.caltrainnow.core.model.*
import com.caltrainnow.core.util.GeoUtils
import com.caltrainnow.core.util.TimeUtils
import java.time.LocalDateTime

/**
 * Central query engine for looking up the next trains.
 * Orchestrates GeoUtils, DirectionResolver, ServiceResolver, and the data source.
 *
 * Pure Kotlin — no Android dependencies.
 */
class LookupEngine(
    private val dataSource: ScheduleDataSource,
    private val directionResolver: DirectionResolver,
    private val serviceResolver: ServiceResolver
) {

    /**
     * Look up the next trains from the nearest station.
     *
     * @param currentLat Current latitude
     * @param currentLng Current longitude
     * @param currentDateTime Current local date/time (America/Los_Angeles)
     * @param limit Number of trains to return (default 2)
     * @return TrainLookupResult with nearest station, direction, and next trains
     */
    suspend fun lookupNextTrains(
        currentLat: Double,
        currentLng: Double,
        currentDateTime: LocalDateTime,
        limit: Int = 2,
        directionOverride: Direction? = null
    ): TrainLookupResult {
        // 1. Get all stations
        val allStations = dataSource.getAllStations()
        require(allStations.isNotEmpty()) { "No stations loaded. Run initialize() first." }

        // 2. Find nearest station (with Home/Work bias)
        val preferredStationIds = getPreferredStationIds(allStations)
        val nearestStation = GeoUtils.findNearestStation(
            currentLat, 
            currentLng, 
            allStations,
            preferredStationIds = preferredStationIds
        ) ?: throw IllegalStateException("Could not find nearest station")

        val distanceMeters = GeoUtils.distanceToStation(currentLat, currentLng, nearestStation)

        // 3. Determine direction (use override if provided)
        val directionResult = if (directionOverride != null) {
            DirectionResolver.DirectionResult(
                direction = directionOverride,
                reason = "Manual override",
                destinationStationId = directionResolver.resolve(currentLat, currentLng, allStations)
                    .let { autoResult ->
                        // When overriding, the destination is the opposite of auto-detect
                        if (directionOverride == autoResult.direction) {
                            autoResult.destinationStationId
                        } else {
                            // Swap: if auto says go to work, override means go home
                            directionResolver.resolve(currentLat, currentLng, allStations)
                                .let { r ->
                                    val autoDestId = r.destinationStationId
                                    // Find the "other" destination
                                    val homeStation = GeoUtils.findNearestStation(
                                        directionResolver.userConfig.homeLatitude,
                                        directionResolver.userConfig.homeLongitude,
                                        allStations
                                    )
                                    val workStation = GeoUtils.findNearestStation(
                                        directionResolver.userConfig.workLatitude,
                                        directionResolver.userConfig.workLongitude,
                                        allStations
                                    )
                                    if (autoDestId == workStation?.stationId) {
                                        homeStation?.stationId
                                    } else {
                                        workStation?.stationId
                                    }
                                }
                        }
                    }
            )
        } else {
            directionResolver.resolve(currentLat, currentLng, allStations)
        }

        // 4. Get active services for today
        val calendars = dataSource.getServiceCalendars()
        val exceptions = dataSource.getServiceExceptions()
        val activeServiceIds = serviceResolver.getActiveServiceIds(
            currentDateTime.toLocalDate(),
            calendars,
            exceptions
        )

        if (activeServiceIds.isEmpty()) {
            return TrainLookupResult(
                nearestStation = nearestStation.toStationInfo(),
                direction = directionResult.direction,
                directionReason = directionResult.reason,
                nextTrains = emptyList(),
                stationDistanceMeters = distanceMeters
            )
        }

        // 5. Build station IDs to query (parent + children)
        val stationIds = buildStationIds(nearestStation)

        // 6. Get current time in GTFS format
        val currentGtfsTime = TimeUtils.currentGtfsTime(currentDateTime.toLocalTime())
        val timeStr = TimeUtils.toSortableTimeString(currentGtfsTime)

        // 7. Query next departures
        val departures = dataSource.getNextDepartures(
            stationIds = stationIds,
            direction = directionResult.direction.gtfsValue,
            serviceIds = activeServiceIds,
            afterTime = timeStr,
            limit = limit
        )

        // 8. Build destination station IDs for arrival time lookup
        val destStationIds = if (directionResult.destinationStationId != null) {
            buildStationIds(directionResult.destinationStationId)
        } else {
            emptyList()
        }

        // 9. Build TrainDeparture results with arrival times
        val trainDepartures = departures.map { dep ->
            val arrivalAtDest = if (destStationIds.isNotEmpty()) {
                dataSource.getArrivalAtStation(dep.tripId, destStationIds)
            } else null

            TrainDeparture(
                tripId = dep.tripId,
                departureTime = TimeUtils.formatForDisplay(dep.departureTime),
                arrivalTimeAtDestination = arrivalAtDest?.let {
                    TimeUtils.formatForDisplay(it.arrivalTime)
                },
                minutesUntilDeparture = TimeUtils.minutesUntil(
                    currentGtfsTime,
                    TimeUtils.parseGtfsTime(dep.departureTime)
                ),
                headsign = dep.tripHeadsign ?: directionResult.direction.name,
                routeType = resolveRouteType(dep.routeShortName, dep.tripId),
                trainNumber = extractTrainNumber(dep.tripId)
            )
        }

        return TrainLookupResult(
            nearestStation = nearestStation.toStationInfo(),
            direction = directionResult.direction,
            directionReason = directionResult.reason,
            nextTrains = trainDepartures,
            stationDistanceMeters = distanceMeters
        )
    }

    /**
     * Identify the GTFS station IDs corresponding to the user's Home and Work.
     */
    private fun getPreferredStationIds(allStations: List<Station>): Set<String> {
        val config = directionResolver.userConfig
        val homeStation = GeoUtils.findNearestStation(config.homeLatitude, config.homeLongitude, allStations)
        val workStation = GeoUtils.findNearestStation(config.workLatitude, config.workLongitude, allStations)
        
        return listOfNotNull(homeStation?.stationId, workStation?.stationId).toSet()
    }

    /**
     * Build the list of station IDs to query — includes parent and all children.
     */
    private suspend fun buildStationIds(station: Station): List<String> {
        val ids = mutableListOf(station.stationId)
        if (station.locationType == 1) {
            // This is a parent station — also include child platforms
            ids.addAll(dataSource.getChildStationIds(station.stationId))
        } else if (station.parentId != null) {
            // This is a child — also include parent and siblings
            ids.add(station.parentId)
            ids.addAll(dataSource.getChildStationIds(station.parentId))
        }
        return ids.distinct()
    }

    /**
     * Build station IDs from a station ID string (looks up the station first).
     */
    private suspend fun buildStationIds(stationId: String): List<String> {
        val station = dataSource.getStationById(stationId)
        return if (station != null) {
            buildStationIds(station)
        } else {
            listOf(stationId)
        }
    }

    /**
     * Resolve the route type display name from GTFS data.
     * Caltrain uses: Local (1XX), Limited (4XX), Express (5XX),
     * Weekend Local (6XX), South County Connector (8XX).
     */
    private fun resolveRouteType(routeShortName: String?, tripId: String): String {
        if (!routeShortName.isNullOrBlank()) return routeShortName

        // Fallback: infer from train number in trip ID
        val trainNum = extractTrainNumber(tripId).toIntOrNull() ?: return "Local"
        return when {
            trainNum in 100..199 -> "Local"
            trainNum in 400..499 -> "Limited"
            trainNum in 500..599 -> "Express"
            trainNum in 600..699 -> "Weekend Local"
            trainNum in 800..899 -> "South County"
            else -> "Local"
        }
    }

    /**
     * Extract a train number from the trip ID.
     * GTFS trip IDs often contain the train number (e.g., "101" from various formats).
     */
    private fun extractTrainNumber(tripId: String): String {
        // Try to extract a numeric portion that looks like a train number
        val match = Regex("(\\d{3})").find(tripId)
        return match?.value ?: tripId
    }

    private fun Station.toStationInfo(): StationInfo {
        return StationInfo(
            stationId = stationId,
            name = stationName,
            latitude = latitude,
            longitude = longitude
        )
    }
}
