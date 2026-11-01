package com.caltrainnow.core.engine

import com.caltrainnow.core.model.AlertEntitySelector
import com.caltrainnow.core.model.AlertPeriod
import com.caltrainnow.core.model.Direction
import com.caltrainnow.core.model.TransitAlert
import com.caltrainnow.core.model.TrainDeparture
import com.caltrainnow.core.model.TrainRealtimeStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RealtimeMatcherTest {
    private val northboundTrain = TrainDeparture(
        tripId = "trip-nb-101",
        departureTime = "8:30 AM",
        arrivalTimeAtDestination = "9:20 AM",
        minutesUntilDeparture = 10,
        headsign = "San Francisco",
        routeType = "Local",
        trainNumber = "101",
        routeId = "caltrain-local",
        boardingStopIds = listOf("station-pa", "station-pa-platform"),
        destinationStopIds = listOf("station-sf", "station-sf-platform"),
        serviceDate = "20261101"
    )

    @Test
    fun relevantAlerts_filtersOutWrongDirection() {
        val northbound = alert("nb", AlertEntitySelector(directionId = Direction.NORTHBOUND.gtfsValue))
        val southbound = alert("sb", AlertEntitySelector(directionId = Direction.SOUTHBOUND.gtfsValue))

        val result = RealtimeMatcher.relevantAlerts(
            alerts = listOf(northbound, southbound),
            direction = Direction.NORTHBOUND,
            trains = listOf(northboundTrain),
            nowEpochSeconds = 100
        )

        assertEquals(listOf("nb"), result.map { it.id })
    }

    @Test
    fun relevantAlerts_includesAgencyWideAndExcludesExpired() {
        val agencyWide = alert("all", AlertEntitySelector())
        val expired = alert(
            "expired",
            AlertEntitySelector(),
            periods = listOf(AlertPeriod(startEpochSeconds = 1, endEpochSeconds = 50))
        )

        val result = RealtimeMatcher.relevantAlerts(
            alerts = listOf(agencyWide, expired),
            direction = Direction.SOUTHBOUND,
            trains = listOf(northboundTrain),
            nowEpochSeconds = 100
        )

        assertEquals(listOf("all"), result.map { it.id })
    }

    @Test
    fun trainUpdates_requiresExactTripAndNeverInfersOnTime() {
        val result = RealtimeMatcher.trainUpdates(
            trains = listOf(northboundTrain),
            realtimeTrips = listOf(
                RealtimeTripUpdate(tripId = "trip-sb-101", delaySeconds = 0)
            )
        )

        assertEquals(TrainRealtimeStatus.NO_UPDATE, result.getValue(northboundTrain.tripId).status)
    }

    @Test
    fun trainUpdates_matchesZeroDelayAndCancellation() {
        val onTime = RealtimeMatcher.trainUpdates(
            trains = listOf(northboundTrain),
            realtimeTrips = listOf(
                RealtimeTripUpdate(
                    tripId = northboundTrain.tripId,
                    serviceDate = northboundTrain.serviceDate,
                    delaySeconds = 0
                )
            )
        )
        val canceled = RealtimeMatcher.trainUpdates(
            trains = listOf(northboundTrain),
            realtimeTrips = listOf(
                RealtimeTripUpdate(tripId = northboundTrain.tripId, canceled = true)
            )
        )

        assertEquals(TrainRealtimeStatus.ON_TIME, onTime.getValue(northboundTrain.tripId).status)
        assertEquals(TrainRealtimeStatus.CANCELED, canceled.getValue(northboundTrain.tripId).status)
    }

    @Test
    fun trainUpdates_matchesStopIdsAndUsesStationSpecificDelay() {
        val result = RealtimeMatcher.trainUpdates(
            trains = listOf(northboundTrain),
            realtimeTrips = listOf(
                RealtimeTripUpdate(
                    tripId = northboundTrain.tripId,
                    stops = listOf(
                        RealtimeStopUpdate(
                            stopId = "station-pa-platform",
                            departureDelaySeconds = 301,
                            departureEpochSeconds = 1_793_557_560
                        ),
                        RealtimeStopUpdate(
                            stopId = "station-sf-platform",
                            arrivalDelaySeconds = 420,
                            arrivalEpochSeconds = 1_793_560_560
                        )
                    )
                )
            )
        ).getValue(northboundTrain.tripId)

        assertEquals(TrainRealtimeStatus.DELAYED, result.status)
        assertEquals(6, result.delayMinutes)
        assertEquals("10:26 AM", result.updatedDepartureTime)
        assertEquals("11:16 AM", result.updatedArrivalTime)
    }

    @Test
    fun isActive_checksStartAndEndBounds() {
        val period = listOf(AlertPeriod(startEpochSeconds = 100, endEpochSeconds = 200))

        assertFalse(RealtimeMatcher.isActive(period, 99))
        assertTrue(RealtimeMatcher.isActive(period, 100))
        assertFalse(RealtimeMatcher.isActive(period, 200))
    }

    private fun alert(
        id: String,
        selector: AlertEntitySelector,
        periods: List<AlertPeriod> = emptyList()
    ) = TransitAlert(
        id = id,
        title = id,
        description = null,
        url = null,
        severity = null,
        periods = periods,
        informedEntities = listOf(selector)
    )
}
