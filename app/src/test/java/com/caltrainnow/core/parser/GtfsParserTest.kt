package com.caltrainnow.core.parser

import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.StringReader

class GtfsParserTest {

    private lateinit var parser: GtfsParser

    @Before
    fun setup() {
        parser = GtfsParser()
    }

    // ── stops.txt ──────────────────────────────────────────────────

    @Test
    fun `parseStops - valid file - returns all stations`() {
        val reader = loadFixture("gtfs/stops.txt")
        val stations = parser.parseStops(reader)

        assertThat(stations).hasSize(10) // 5 parent + 5 child
        assertThat(stations.map { it.stationName }).contains("San Francisco")
        assertThat(stations.map { it.stationName }).contains("Palo Alto")
        assertThat(stations.map { it.stationName }).contains("San Jose Diridon")
    }

    @Test
    fun `parseStops - handles parent and child stops`() {
        val reader = loadFixture("gtfs/stops.txt")
        val stations = parser.parseStops(reader)

        val parents = stations.filter { it.locationType == 1 }
        val children = stations.filter { it.locationType == 0 }

        assertThat(parents).hasSize(5)
        assertThat(children).hasSize(5)
        assertThat(children.all { it.parentId != null }).isTrue()
    }

    @Test
    fun `parseStops - parses coordinates correctly`() {
        val reader = loadFixture("gtfs/stops.txt")
        val stations = parser.parseStops(reader)

        val sf = stations.first { it.stationId == "stn_sf" }
        assertThat(sf.latitude).isWithin(0.001).of(37.7764)
        assertThat(sf.longitude).isWithin(0.001).of(-122.3943)
    }

    @Test
    fun `parseStops - malformed row - skips without crashing`() {
        val csv = """
            stop_id,stop_name,stop_lat,stop_lon,location_type,parent_station
            good_stop,Good Station,37.0,-122.0,1,
            bad_stop,Missing Coords,,,0,
            another_good,Another Good,37.5,-122.5,1,
        """.trimIndent()
        val stations = parser.parseStops(BufferedReader(StringReader(csv)))

        assertThat(stations).hasSize(2)
    }

    @Test
    fun `parseStops - empty file - returns empty list`() {
        val csv = "stop_id,stop_name,stop_lat,stop_lon,location_type,parent_station"
        val stations = parser.parseStops(BufferedReader(StringReader(csv)))

        assertThat(stations).isEmpty()
    }

    @Test
    fun `parseStops - file with BOM - handles correctly`() {
        val csv = "\uFEFFstop_id,stop_name,stop_lat,stop_lon,location_type,parent_station\n" +
            "stn_test,Test Station,37.0,-122.0,1,"
        val stations = parser.parseStops(BufferedReader(StringReader(csv)))

        assertThat(stations).hasSize(1)
        assertThat(stations[0].stationId).isEqualTo("stn_test")
    }

    // ── trips.txt ──────────────────────────────────────────────────

    @Test
    fun `parseTrips - valid file - returns correct trips`() {
        val reader = loadFixture("gtfs/trips.txt")
        val trips = parser.parseTrips(reader)

        assertThat(trips).hasSize(10)
        assertThat(trips.filter { it.direction == 0 }).hasSize(6) // 5 weekday NB + 1 weekend NB
        assertThat(trips.filter { it.direction == 1 }).hasSize(4) // 4 weekday SB
    }

    @Test
    fun `parseTrips - parses direction and service correctly`() {
        val reader = loadFixture("gtfs/trips.txt")
        val trips = parser.parseTrips(reader)

        val express = trips.first { it.tripId == "trip_507_nb" }
        assertThat(express.routeId).isEqualTo("route_express")
        assertThat(express.serviceId).isEqualTo("svc_weekday")
        assertThat(express.direction).isEqualTo(0)
        assertThat(express.tripHeadsign).isEqualTo("San Francisco")
    }

    // ── stop_times.txt ─────────────────────────────────────────────

    @Test
    fun `parseStopTimes - valid file - returns correct stop times`() {
        val reader = loadFixture("gtfs/stop_times.txt")
        val stopTimes = parser.parseStopTimes(reader)

        assertThat(stopTimes).hasSize(50) // 10 trips × 5 stops each
    }

    @Test
    fun `parseStopTimes - handles after-midnight times`() {
        val reader = loadFixture("gtfs/stop_times.txt")
        val stopTimes = parser.parseStopTimes(reader)

        val lateTrip = stopTimes.filter { it.tripId == "trip_171_nb" }
        assertThat(lateTrip).hasSize(5)
        assertThat(lateTrip[0].departureTime).isEqualTo("25:48:00")
    }

    @Test
    fun `parseStopTimes - stop sequences are ascending per trip`() {
        val reader = loadFixture("gtfs/stop_times.txt")
        val stopTimes = parser.parseStopTimes(reader)

        val tripGroups = stopTimes.groupBy { it.tripId }
        for ((tripId, stops) in tripGroups) {
            val sequences = stops.map { it.stopSequence }
            assertThat(sequences).isInOrder()
        }
    }

    // ── calendar.txt ───────────────────────────────────────────────

    @Test
    fun `parseCalendar - valid file - returns services`() {
        val reader = loadFixture("gtfs/calendar.txt")
        val calendars = parser.parseCalendar(reader)

        assertThat(calendars).hasSize(2)

        val weekday = calendars.first { it.serviceId == "svc_weekday" }
        assertThat(weekday.monday).isTrue()
        assertThat(weekday.friday).isTrue()
        assertThat(weekday.saturday).isFalse()
        assertThat(weekday.sunday).isFalse()

        val weekend = calendars.first { it.serviceId == "svc_weekend" }
        assertThat(weekend.monday).isFalse()
        assertThat(weekend.saturday).isTrue()
        assertThat(weekend.sunday).isTrue()
    }

    // ── calendar_dates.txt ─────────────────────────────────────────

    @Test
    fun `parseCalendarDates - handles exceptions`() {
        val reader = loadFixture("gtfs/calendar_dates.txt")
        val exceptions = parser.parseCalendarDates(reader)

        assertThat(exceptions).hasSize(2)

        val removed = exceptions.first { it.exceptionType == 2 }
        assertThat(removed.serviceId).isEqualTo("svc_weekday")
        assertThat(removed.date).isEqualTo("20251225")

        val added = exceptions.first { it.exceptionType == 1 }
        assertThat(added.serviceId).isEqualTo("svc_weekend")
    }

    // ── routes.txt ─────────────────────────────────────────────────

    @Test
    fun `parseRoutes - valid file - returns routes`() {
        val reader = loadFixture("gtfs/routes.txt")
        val routes = parser.parseRoutes(reader)

        assertThat(routes).hasSize(3)
        assertThat(routes.map { it.routeShortName }).containsExactly("Local", "Limited", "Express")
    }

    // ── Helpers ─────────────────────────────────────────────────────

    private fun loadFixture(path: String): BufferedReader {
        val stream = javaClass.classLoader?.getResourceAsStream(path)
            ?: throw IllegalStateException("Test fixture not found: $path")
        return BufferedReader(InputStreamReader(stream))
    }
}
