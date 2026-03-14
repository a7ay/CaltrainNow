package com.caltrainnow.data.repository

import com.caltrainnow.core.engine.LookupEngine
import com.caltrainnow.core.model.*
import com.caltrainnow.core.parser.GtfsParser
import com.caltrainnow.core.validation.ScheduleValidator
import com.caltrainnow.data.db.RoomScheduleDataSource
import com.caltrainnow.data.gtfs.GtfsDownloader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.File
import java.io.FileReader
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Main repository that orchestrates GTFS download, parsing, validation,
 * storage, and train lookups.
 *
 * This is the primary entry point for the ViewModel layer.
 */
@Singleton
class CaltrainRepository @Inject constructor(
    private val downloader: GtfsDownloader,
    private val parser: GtfsParser,
    private val dataSource: RoomScheduleDataSource,
    private val validator: ScheduleValidator,
    private val lookupEngine: LookupEngine
) {

    companion object {
        private val PT_ZONE = ZoneId.of("America/Los_Angeles")
    }

    /**
     * Initialize the schedule: download GTFS, parse, validate, and store.
     *
     * Safe flow:
     * 1. Download and parse into memory
     * 2. Validate parsed data
     * 3. If valid: wipe old data + insert new in one go
     * 4. If invalid: keep old data, return errors
     */
    suspend fun initialize(url: String = GtfsDownloader.GTFS_URL): InitResult =
        withContext(Dispatchers.IO) {
            try {
                // Step 1: Download and extract GTFS
                val gtfsDir = downloader.download(url)

                // Step 2: Parse all CSV files
                val stations = parseFile(gtfsDir, "stops.txt") { parser.parseStops(it) }
                val trips = parseFile(gtfsDir, "trips.txt") { parser.parseTrips(it) }
                val stopTimes = parseFile(gtfsDir, "stop_times.txt") { parser.parseStopTimes(it) }
                val routes = parseFile(gtfsDir, "routes.txt") { parser.parseRoutes(it) }
                val calendars = parseFile(gtfsDir, "calendar.txt") { parser.parseCalendar(it) }
                val calendarDates = parseFileOptional(gtfsDir, "calendar_dates.txt") {
                    parser.parseCalendarDates(it)
                }

                // Step 3: Store parsed data (clear old, insert new)
                dataSource.clearAll()
                dataSource.insertStations(stations)
                dataSource.insertRoutes(routes)
                dataSource.insertTrips(trips)

                // Build trip lookup for denormalized stop_times insert
                val tripLookup = trips.associateBy { it.tripId }
                dataSource.insertStopTimesWithTripInfo(stopTimes, tripLookup)

                dataSource.insertServiceCalendars(calendars)
                dataSource.insertServiceExceptions(calendarDates)

                // Step 4: Store metadata
                val metadata = ScheduleMetadata(
                    downloadedAt = LocalDateTime.now(PT_ZONE)
                        .format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
                    gtfsUrl = url,
                    stationCount = stations.size,
                    tripCount = trips.size,
                    stopTimeCount = stopTimes.size
                )
                dataSource.insertMetadata(metadata)

                // Step 5: Validate
                val validationResult = validator.validate()

                // Step 6: Clean up temp files
                downloader.cleanup()

                InitResult(
                    success = validationResult.isValid,
                    stationCount = stations.size,
                    tripCount = trips.size,
                    stopTimeCount = stopTimes.size,
                    validationResult = validationResult
                )
            } catch (e: Exception) {
                downloader.cleanup()
                InitResult(
                    success = false,
                    errorMessage = "Initialization failed: ${e.message}"
                )
            }
        }

    /**
     * Run validation checks on the currently loaded schedule.
     */
    suspend fun validate(): ValidationResult {
        return validator.validate()
    }

    /**
     * Look up the next trains from the nearest station.
     */
    suspend fun lookupNextTrains(
        lat: Double,
        lng: Double,
        dateTime: LocalDateTime = LocalDateTime.now(PT_ZONE)
    ): TrainLookupResult {
        return lookupEngine.lookupNextTrains(lat, lng, dateTime)
    }

    /**
     * Look up the next trains with an explicit direction override.
     */
    suspend fun lookupNextTrainsWithDirection(
        lat: Double,
        lng: Double,
        direction: Direction,
        dateTime: LocalDateTime = LocalDateTime.now(PT_ZONE)
    ): TrainLookupResult {
        return lookupEngine.lookupNextTrains(lat, lng, dateTime, directionOverride = direction)
    }

    /**
     * Check if schedule data has been loaded.
     */
    suspend fun isScheduleLoaded(): Boolean {
        return dataSource.getMetadata() != null
    }

    /**
     * Get schedule metadata (last download time, counts, etc.)
     */
    suspend fun getScheduleMetadata(): ScheduleMetadata? {
        return dataSource.getMetadata()
    }

    /**
     * Get all stations from the data source.
     */
    suspend fun getAllStations(): List<Station> {
        return dataSource.getAllStations()
    }

    // ── Helpers ────────────────────────────────────────────────────

    private fun <T> parseFile(
        dir: File,
        filename: String,
        parse: (BufferedReader) -> List<T>
    ): List<T> {
        val file = File(dir, filename)
        if (!file.exists()) {
            throw IllegalStateException("Required GTFS file missing: $filename")
        }
        return BufferedReader(FileReader(file)).use { reader ->
            parse(reader)
        }
    }

    private fun <T> parseFileOptional(
        dir: File,
        filename: String,
        parse: (BufferedReader) -> List<T>
    ): List<T> {
        val file = File(dir, filename)
        if (!file.exists()) return emptyList()
        return BufferedReader(FileReader(file)).use { reader ->
            parse(reader)
        }
    }
}
