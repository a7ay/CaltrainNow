package com.caltrainnow.core.util

import com.caltrainnow.core.model.Station
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class GeoUtilsTest {

    // ── Haversine distance ─────────────────────────────────────────

    @Test
    fun `haversineDistance - SF to SJ Diridon - approximately 48-49 km`() {
        val distance = GeoUtils.haversineDistance(
            37.7764, -122.3943,  // SF
            37.3297, -121.9020   // SJ Diridon
        )
        // Should be approximately 48-49 km
        assertThat(distance).isGreaterThan(47_000.0)
        assertThat(distance).isLessThan(70_000.0)
    }

    @Test
    fun `haversineDistance - same point - returns zero`() {
        val distance = GeoUtils.haversineDistance(37.0, -122.0, 37.0, -122.0)
        assertThat(distance).isWithin(0.1).of(0.0)
    }

    @Test
    fun `haversineDistance - nearby points - returns small value`() {
        // ~100 meters apart
        val distance = GeoUtils.haversineDistance(
            37.4437, -122.1648,
            37.4446, -122.1648
        )
        assertThat(distance).isGreaterThan(50.0)
        assertThat(distance).isLessThan(200.0)
    }

    // ── Find nearest station ───────────────────────────────────────

    private val testStations = listOf(
        Station("stn_sf", "San Francisco", 37.7764, -122.3943, null, 1),
        Station("stn_millbrae", "Millbrae", 37.5999, -122.3866, null, 1),
        Station("stn_paloalto", "Palo Alto", 37.4437, -122.1648, null, 1),
        Station("stn_mountainview", "Mountain View", 37.3946, -122.0764, null, 1),
        Station("stn_sanjose", "San Jose Diridon", 37.3297, -121.9020, null, 1)
    )

    @Test
    fun `findNearestStation - at Palo Alto coords - returns Palo Alto`() {
        val nearest = GeoUtils.findNearestStation(37.4437, -122.1648, testStations)
        assertThat(nearest?.stationId).isEqualTo("stn_paloalto")
    }

    @Test
    fun `findNearestStation - between MV and PA biased toward MV - returns MV`() {
        // Slightly closer to Mountain View
        val nearest = GeoUtils.findNearestStation(37.4100, -122.1100, testStations)
        assertThat(nearest?.stationId).isEqualTo("stn_mountainview")
    }

    @Test
    fun `findNearestStation - at SF coords - returns SF`() {
        val nearest = GeoUtils.findNearestStation(37.7764, -122.3943, testStations)
        assertThat(nearest?.stationId).isEqualTo("stn_sf")
    }

    @Test
    fun `findNearestStation - empty list - returns null`() {
        val nearest = GeoUtils.findNearestStation(37.0, -122.0, emptyList())
        assertThat(nearest).isNull()
    }

    @Test
    fun `findNearestStation - prefers parent stations over children`() {
        val mixedStations = listOf(
            Station("stn_pa", "Palo Alto", 37.4437, -122.1648, null, 1),
            Station("plt_pa_nb", "Palo Alto NB", 37.4437, -122.1647, "stn_pa", 0)
        )
        val nearest = GeoUtils.findNearestStation(37.4437, -122.1648, mixedStations)
        assertThat(nearest?.stationId).isEqualTo("stn_pa")
    }

    // ── Distance to station ────────────────────────────────────────

    @Test
    fun `distanceToStation - returns correct distance`() {
        val station = Station("stn_pa", "Palo Alto", 37.4437, -122.1648, null, 1)
        val distance = GeoUtils.distanceToStation(37.4437, -122.1650, station)
        assertThat(distance).isGreaterThan(0.0)
        assertThat(distance).isLessThan(50.0) // Should be just a few meters
    }
}
