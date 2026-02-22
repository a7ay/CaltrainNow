package com.caltrainnow.core.validation

import com.caltrainnow.core.datasource.ScheduleDataSource
import com.caltrainnow.core.engine.ServiceResolver
import com.caltrainnow.core.model.Direction
import com.caltrainnow.core.model.ValidationResult
import java.time.LocalDate

/**
 * Validates the integrity and completeness of loaded GTFS schedule data.
 * Runs after parsing and before committing data to production tables.
 *
 * Pure Kotlin — no Android dependencies.
 */
class ScheduleValidator(
    private val dataSource: ScheduleDataSource,
    private val serviceResolver: ServiceResolver
) {

    companion object {
        private const val MIN_EXPECTED_STATIONS = 20
        private const val MIN_EXPECTED_TRIPS = 10

        /** Anchor stations that must exist in any valid Caltrain schedule. */
        private val ANCHOR_STATIONS = listOf(
            "San Francisco",
            "San Jose Diridon",
            "Palo Alto",
            "Mountain View",
            "Millbrae"
        )
    }

    /**
     * Run all validation checks and return a combined result.
     */
    suspend fun validate(): ValidationResult {
        val errors = mutableListOf<String>()
        val warnings = mutableListOf<String>()

        // Check station count
        val stationCount = dataSource.getStationCount()
        if (stationCount == 0) {
            errors.add("No stations found in schedule data.")
        } else if (stationCount < MIN_EXPECTED_STATIONS) {
            warnings.add("Only $stationCount stations found (expected at least $MIN_EXPECTED_STATIONS).")
        }

        // Check trip count
        val tripCount = dataSource.getTripCount()
        if (tripCount == 0) {
            errors.add("No trips found in schedule data.")
        } else if (tripCount < MIN_EXPECTED_TRIPS) {
            warnings.add("Only $tripCount trips found (expected at least $MIN_EXPECTED_TRIPS).")
        }

        // Check trips in both directions
        val nbCount = dataSource.getTripCountByDirection(Direction.NORTHBOUND.gtfsValue)
        val sbCount = dataSource.getTripCountByDirection(Direction.SOUTHBOUND.gtfsValue)
        if (nbCount == 0) {
            errors.add("No northbound trips found.")
        }
        if (sbCount == 0) {
            errors.add("No southbound trips found.")
        }

        // Check stop times
        val stopTimeCount = dataSource.getStopTimeCount()
        if (stopTimeCount == 0) {
            errors.add("No stop times found in schedule data.")
        }

        // Check for orphaned stop times (referencing non-existent trips/stations)
        if (stopTimeCount > 0) {
            val hasOrphans = dataSource.hasOrphanedStopTimes()
            if (hasOrphans) {
                warnings.add("Some stop times reference non-existent trips or stations.")
            }
        }

        // Check stop sequence ordering
        if (stopTimeCount > 0) {
            val validSequences = dataSource.hasValidStopSequences()
            if (!validSequences) {
                warnings.add("Some trips have non-sequential stop ordering.")
            }
        }

        // Check anchor stations exist
        for (stationName in ANCHOR_STATIONS) {
            val exists = dataSource.stationExists(stationName)
            if (!exists) {
                warnings.add("Expected station '$stationName' not found.")
            }
        }

        // Check active services for today
        try {
            val calendars = dataSource.getServiceCalendars()
            val exceptions = dataSource.getServiceExceptions()
            val activeToday = serviceResolver.getActiveServiceIds(
                LocalDate.now(),
                calendars,
                exceptions
            )
            if (activeToday.isEmpty()) {
                warnings.add(
                    "No active service found for today (${LocalDate.now()}). " +
                    "This may be normal on a holiday, or the schedule may be outdated."
                )
            }
        } catch (e: Exception) {
            warnings.add("Could not check active services: ${e.message}")
        }

        return ValidationResult(
            isValid = errors.isEmpty(),
            errors = errors,
            warnings = warnings
        )
    }
}
