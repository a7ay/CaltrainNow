package com.caltrainnow.core.engine

import com.caltrainnow.core.model.Direction
import com.caltrainnow.core.model.Station
import com.caltrainnow.core.model.UserConfig
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class DirectionResolverTest {

    private val testStations = listOf(
        Station("stn_sf", "San Francisco", 37.7764, -122.3943, null, 1),
        Station("stn_millbrae", "Millbrae", 37.5999, -122.3866, null, 1),
        Station("stn_paloalto", "Palo Alto", 37.4437, -122.1648, null, 1),
        Station("stn_mountainview", "Mountain View", 37.3946, -122.0764, null, 1),
        Station("stn_sanjose", "San Jose Diridon", 37.3297, -121.9020, null, 1)
    )

    // Default config: home in Sunnyvale (south), work in SF (north)
    private val config = UserConfig(
        homeLatitude = 37.3688,
        homeLongitude = -122.0363,
        homeLabel = "Sunnyvale",
        workLatitude = 37.7764,
        workLongitude = -122.3943,
        workLabel = "San Francisco"
    )

    private val resolver = DirectionResolver(config)

    @Test
    fun `resolve - near home (Sunnyvale) - returns northbound to work`() {
        // At home in Sunnyvale → heading to work in SF → northbound
        val result = resolver.resolve(37.3688, -122.0363, testStations)
        assertThat(result.direction).isEqualTo(Direction.NORTHBOUND)
        assertThat(result.reason).contains("home")
        assertThat(result.reason).contains("work")
    }

    @Test
    fun `resolve - near work (SF) - returns southbound to home`() {
        // At work in SF → heading home to Sunnyvale → southbound
        val result = resolver.resolve(37.7764, -122.3943, testStations)
        assertThat(result.direction).isEqualTo(Direction.SOUTHBOUND)
        assertThat(result.reason).contains("work")
        assertThat(result.reason).contains("home")
    }

    @Test
    fun `resolve - at Palo Alto - closer to home - returns northbound`() {
        // Palo Alto is closer to Sunnyvale (home) than SF (work)
        val result = resolver.resolve(37.4437, -122.1648, testStations)
        assertThat(result.direction).isEqualTo(Direction.NORTHBOUND)
    }

    @Test
    fun `resolve - at Millbrae - closer to work - returns southbound`() {
        // Millbrae is closer to SF (work) than Sunnyvale (home)
        val result = resolver.resolve(37.5999, -122.3866, testStations)
        assertThat(result.direction).isEqualTo(Direction.SOUTHBOUND)
    }

    @Test
    fun `resolve - reversed config (home north, work south) - reverses direction`() {
        // Home in SF (north), work in SJ (south)
        val reversedConfig = UserConfig(
            homeLatitude = 37.7764,
            homeLongitude = -122.3943,
            homeLabel = "San Francisco",
            workLatitude = 37.3297,
            workLongitude = -121.9020,
            workLabel = "San Jose"
        )
        val reversedResolver = DirectionResolver(reversedConfig)

        // Near home (SF) → heading to work (SJ) → southbound
        val result = reversedResolver.resolve(37.7764, -122.3943, testStations)
        assertThat(result.direction).isEqualTo(Direction.SOUTHBOUND)
    }

    @Test
    fun `resolve - returns destination station ID`() {
        // Near home → destination should be work station
        val result = resolver.resolve(37.3688, -122.0363, testStations)
        assertThat(result.destinationStationId).isNotNull()
        assertThat(result.destinationStationId).isEqualTo("stn_sf") // nearest to work
    }

    @Test
    fun `resolve - empty station list - returns default northbound`() {
        val result = resolver.resolve(37.0, -122.0, emptyList())
        assertThat(result.direction).isEqualTo(Direction.NORTHBOUND)
        assertThat(result.reason).contains("default")
    }
}
