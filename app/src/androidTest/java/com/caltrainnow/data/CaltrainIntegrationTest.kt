package com.caltrainnow.data

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.caltrainnow.core.engine.DirectionResolver
import com.caltrainnow.core.engine.LookupEngine
import com.caltrainnow.core.engine.ServiceResolver
import com.caltrainnow.core.model.*
import com.caltrainnow.core.parser.GtfsParser
import com.caltrainnow.core.validation.ScheduleValidator
import com.caltrainnow.data.db.CaltrainDatabase
import com.caltrainnow.data.db.RoomScheduleDataSource
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.BufferedReader
import java.io.InputStreamReader
import java.time.LocalDateTime

/**
 * Integration tests that use a real in-memory Room database
 * and the actual GTFS parser with fixture data.
 *
 * These verify the full flow: parse → insert → validate → lookup.
 */
@RunWith(AndroidJUnit4::class)
class CaltrainIntegrationTest {

    private lateinit var db: CaltrainDatabase
    private lateinit var dataSource: RoomScheduleDataSource
    private lateinit var parser: GtfsParser
    private lateinit var serviceResolver: ServiceResolver
    private lateinit var validator: ScheduleValidator
    private lateinit var lookupEngine: LookupEngine

    // Home in Sunnyvale, work in SF
    private val userConfig = UserConfig(
        homeLatitude = 37.3688,
        homeLongitude = -122.0363,
        homeLabel = "Sunnyvale",
        workLatitude = 37.7764,
        workLongitude = -122.3943,
        workLabel = "San Francisco"
    )

    @Before
    fun setup() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        db = Room.inMemoryDatabaseBuilder(context, CaltrainDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dataSource = RoomScheduleDataSource(db)
        parser = GtfsParser()
        serviceResolver = ServiceResolver()
        validator = ScheduleValidator(dataSource, serviceResolver)
        lookupEngine = LookupEngine(
            dataSource,
            DirectionResolver(userConfig),
            serviceResolver
        )
    }

    @After
    fun teardown() {
        db.close()
    }

    // ── Initialize Flow ────────────────────────────────────────────

    @Test
    fun initialize_withValidData_populatesAllTables() = runBlocking {
        loadFixtureData()

        assertThat(dataSource.getStationCount()).isGreaterThan(0)
        assertThat(dataSource.getTripCount()).isGreaterThan(0)
        assertThat(dataSource.getStopTimeCount()).isGreaterThan(0)
    }

    @Test
    fun initialize_wipesOldData_beforeInsertingNew() = runBlocking {
        // Load data twice
        loadFixtureData()
        val firstCount = dataSource.getStationCount()
        loadFixtureData()
        val secondCount = dataSource.getStationCount()

        assertThat(secondCount).isEqualTo(firstCount) // No duplicates
    }

    @Test
    fun initialize_storesCorrectCounts() = runBlocking {
        loadFixtureData()

        assertThat(dataSource.getStationCount()).isEqualTo(10) // 5 parent + 5 child
        assertThat(dataSource.getTripCount()).isEqualTo(10)
        assertThat(dataSource.getStopTimeCount()).isEqualTo(50)
    }

    // ── Validation Flow ────────────────────────────────────────────

    @Test
    fun validate_afterGoodInit_returnsValid() = runBlocking {
        loadFixtureData()
        val result = validator.validate()

        assertThat(result.isValid).isTrue()
        assertThat(result.errors).isEmpty()
    }

    @Test
    fun validate_hasTripsInBothDirections() = runBlocking {
        loadFixtureData()

        val nbCount = dataSource.getTripCountByDirection(Direction.NORTHBOUND.gtfsValue)
        val sbCount = dataSource.getTripCountByDirection(Direction.SOUTHBOUND.gtfsValue)

        assertThat(nbCount).isGreaterThan(0)
        assertThat(sbCount).isGreaterThan(0)
    }

    @Test
    fun validate_anchorStationsExist() = runBlocking {
        loadFixtureData()

        assertThat(dataSource.stationExists("San Francisco")).isTrue()
        assertThat(dataSource.stationExists("San Jose Diridon")).isTrue()
        assertThat(dataSource.stationExists("Palo Alto")).isTrue()
        assertThat(dataSource.stationExists("Mountain View")).isTrue()
        assertThat(dataSource.stationExists("Millbrae")).isTrue()
    }

    @Test
    fun validate_emptyDatabase_returnsInvalid() = runBlocking {
        // Don't load any data
        val result = validator.validate()

        assertThat(result.isValid).isFalse()
        assertThat(result.errors).isNotEmpty()
    }

    // ── Lookup Flow ────────────────────────────────────────────────

    @Test
    fun lookup_nearPaloAlto_weekday8AM_returnsNextTrains() = runBlocking {
        loadFixtureData()

        // Weekday at 8:00 AM near Palo Alto
        val wednesday = LocalDateTime.of(2026, 2, 25, 8, 0) // Wednesday
        val result = lookupEngine.lookupNextTrains(
            37.4437, -122.1648, wednesday, limit = 2
        )

        assertThat(result.nextTrains).isNotEmpty()
        assertThat(result.nextTrains.size).isAtMost(2)
    }

    @Test
    fun lookup_returnsTrainsInChronologicalOrder() = runBlocking {
        loadFixtureData()

        val wednesday = LocalDateTime.of(2026, 2, 25, 5, 0) // 5 AM — early, multiple trains ahead
        val result = lookupEngine.lookupNextTrains(
            37.4437, -122.1648, wednesday, limit = 2
        )

        if (result.nextTrains.size >= 2) {
            assertThat(result.nextTrains[0].minutesUntilDeparture)
                .isLessThan(result.nextTrains[1].minutesUntilDeparture)
        }
    }

    @Test
    fun lookup_nearHome_returnsNorthbound() = runBlocking {
        loadFixtureData()

        val wednesday = LocalDateTime.of(2026, 2, 25, 8, 0)
        val result = lookupEngine.lookupNextTrains(
            userConfig.homeLatitude, userConfig.homeLongitude, wednesday
        )

        assertThat(result.direction).isEqualTo(Direction.NORTHBOUND)
    }

    @Test
    fun lookup_nearWork_returnsSouthbound() = runBlocking {
        loadFixtureData()

        val wednesday = LocalDateTime.of(2026, 2, 25, 17, 0) // 5 PM leaving work
        val result = lookupEngine.lookupNextTrains(
            userConfig.workLatitude, userConfig.workLongitude, wednesday
        )

        assertThat(result.direction).isEqualTo(Direction.SOUTHBOUND)
    }

    @Test
    fun lookup_nearestStation_isCorrect() = runBlocking {
        loadFixtureData()

        val wednesday = LocalDateTime.of(2026, 2, 25, 8, 0)
        val result = lookupEngine.lookupNextTrains(
            37.4437, -122.1648, wednesday // Palo Alto coords
        )

        assertThat(result.nearestStation.name).isEqualTo("Palo Alto")
    }

    @Test
    fun lookup_weekendSchedule_usesWeekendServices() = runBlocking {
        loadFixtureData()

        val saturday = LocalDateTime.of(2026, 2, 28, 8, 0) // Saturday
        val result = lookupEngine.lookupNextTrains(
            37.4437, -122.1648, saturday
        )

        // Weekend schedule exists — should find weekend trains
        // (our fixture has trip_601_wknd at 08:55 from Palo Alto)
        if (result.nextTrains.isNotEmpty()) {
            assertThat(result.direction).isEqualTo(Direction.NORTHBOUND)
        }
    }

    @Test
    fun lookup_returnsStationDistance() = runBlocking {
        loadFixtureData()

        val wednesday = LocalDateTime.of(2026, 2, 25, 8, 0)
        val result = lookupEngine.lookupNextTrains(
            37.4437, -122.1648, wednesday
        )

        assertThat(result.stationDistanceMeters).isGreaterThan(0.0)
    }

    // ── Helper: Load fixture data ──────────────────────────────────

    private suspend fun loadFixtureData() {
        val context = InstrumentationRegistry.getInstrumentation().context

        // Parse fixture files from test assets
        val stations = parser.parseStops(loadAsset("gtfs/stops.txt"))
        val trips = parser.parseTrips(loadAsset("gtfs/trips.txt"))
        val stopTimes = parser.parseStopTimes(loadAsset("gtfs/stop_times.txt"))
        val routes = parser.parseRoutes(loadAsset("gtfs/routes.txt"))
        val calendars = parser.parseCalendar(loadAsset("gtfs/calendar.txt"))
        val calendarDates = parser.parseCalendarDates(loadAsset("gtfs/calendar_dates.txt"))

        // Clear and insert
        dataSource.clearAll()
        dataSource.insertStations(stations)
        dataSource.insertRoutes(routes)
        dataSource.insertTrips(trips)

        val tripLookup = trips.associateBy { it.tripId }
        dataSource.insertStopTimesWithTripInfo(stopTimes, tripLookup)

        dataSource.insertServiceCalendars(calendars)
        dataSource.insertServiceExceptions(calendarDates)

        dataSource.insertMetadata(
            ScheduleMetadata(
                downloadedAt = "2026-02-22T10:00:00",
                gtfsUrl = "test",
                stationCount = stations.size,
                tripCount = trips.size,
                stopTimeCount = stopTimes.size
            )
        )
    }

    private fun loadAsset(path: String): BufferedReader {
        val context = InstrumentationRegistry.getInstrumentation().context
        return BufferedReader(InputStreamReader(context.assets.open(path)))
    }
}
