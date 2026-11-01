package com.caltrainnow.core.engine

import com.caltrainnow.core.model.AlertEntitySelector
import com.caltrainnow.core.model.AlertPeriod
import com.caltrainnow.core.model.Direction
import com.caltrainnow.core.model.TransitAlert
import com.caltrainnow.core.model.TrainDeparture
import com.caltrainnow.core.model.TrainRealtimeStatus
import com.caltrainnow.core.model.TrainRealtimeUpdate
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.ceil

data class RealtimeStopUpdate(
    val stopId: String,
    val arrivalDelaySeconds: Int? = null,
    val departureDelaySeconds: Int? = null,
    val arrivalEpochSeconds: Long? = null,
    val departureEpochSeconds: Long? = null
)

data class RealtimeTripUpdate(
    val tripId: String,
    val serviceDate: String? = null,
    val canceled: Boolean = false,
    val delaySeconds: Int? = null,
    val stops: List<RealtimeStopUpdate> = emptyList()
)

/** Pure matching rules shared by the realtime repository and JVM unit tests. */
object RealtimeMatcher {
    private val pacificZone = ZoneId.of("America/Los_Angeles")
    private val timeFormatter = DateTimeFormatter.ofPattern("h:mm a", Locale.US)

    fun relevantAlerts(
        alerts: List<TransitAlert>,
        direction: Direction,
        trains: List<TrainDeparture>,
        nowEpochSeconds: Long
    ): List<TransitAlert> = alerts.filter { alert ->
        isActive(alert.periods, nowEpochSeconds) &&
            (alert.informedEntities.isEmpty() || alert.informedEntities.any { selector ->
                matchesAlertSelector(selector, direction, trains)
            })
    }

    fun trainUpdates(
        trains: List<TrainDeparture>,
        realtimeTrips: List<RealtimeTripUpdate>
    ): Map<String, TrainRealtimeUpdate> = trains.associate { train ->
        val update = realtimeTrips.firstOrNull { realtime ->
            realtime.tripId == train.tripId &&
                (realtime.serviceDate.isNullOrBlank() || train.serviceDate.isBlank() ||
                    realtime.serviceDate == train.serviceDate)
        }

        train.tripId to (update?.toDisplayUpdate(train) ?: TrainRealtimeUpdate(
            tripId = train.tripId,
            status = TrainRealtimeStatus.NO_UPDATE
        ))
    }

    fun isActive(periods: List<AlertPeriod>, nowEpochSeconds: Long): Boolean {
        if (periods.isEmpty()) return true
        return periods.any { period ->
            (period.startEpochSeconds == null || period.startEpochSeconds <= nowEpochSeconds) &&
                (period.endEpochSeconds == null || period.endEpochSeconds > nowEpochSeconds)
        }
    }

    private fun matchesAlertSelector(
        selector: AlertEntitySelector,
        direction: Direction,
        trains: List<TrainDeparture>
    ): Boolean {
        if (selector.isUnrestricted) return true
        if (selector.directionId != null && selector.directionId != direction.gtfsValue) return false

        val hasTripSelector = selector.tripId != null
        val hasRouteSelector = selector.routeId != null
        val hasStopSelector = selector.stopId != null
        val selectorMatchesDisplayedTrain = trains.any { train ->
            (!hasTripSelector || selector.tripId == train.tripId) &&
                (!hasRouteSelector || selector.routeId == train.routeId) &&
                (!hasStopSelector || selector.stopId?.let { stopId ->
                    stopId in train.boardingStopIds || stopId in train.destinationStopIds
                } == true)
        }

        return if (hasTripSelector || hasRouteSelector || hasStopSelector) {
            selectorMatchesDisplayedTrain
        } else {
            // A direction-only selector is meaningful even without a trip/route/stop selector.
            selector.directionId == direction.gtfsValue
        }
    }

    private fun RealtimeTripUpdate.toDisplayUpdate(train: TrainDeparture): TrainRealtimeUpdate {
        if (canceled) {
            return TrainRealtimeUpdate(train.tripId, TrainRealtimeStatus.CANCELED)
        }

        val boarding = stops.firstOrNull { it.stopId in train.boardingStopIds }
        val destination = stops.firstOrNull { it.stopId in train.destinationStopIds }
        val effectiveDelaySeconds = boarding?.departureDelaySeconds
            ?: destination?.arrivalDelaySeconds
            ?: this.delaySeconds
        val delayMinutes = effectiveDelaySeconds?.takeIf { it >= 0 }?.let { seconds ->
            if (seconds == 0) 0 else ceil(seconds / 60.0).toInt()
        }
        val status = when {
            effectiveDelaySeconds != null && effectiveDelaySeconds > 0 -> TrainRealtimeStatus.DELAYED
            effectiveDelaySeconds == 0 -> TrainRealtimeStatus.ON_TIME
            else -> TrainRealtimeStatus.UPDATED
        }

        return TrainRealtimeUpdate(
            tripId = train.tripId,
            status = status,
            delayMinutes = delayMinutes,
            updatedDepartureTime = boarding?.departureEpochSeconds?.let(::formatTime),
            updatedArrivalTime = destination?.arrivalEpochSeconds?.let(::formatTime)
        )
    }

    private fun formatTime(epochSeconds: Long): String =
        Instant.ofEpochSecond(epochSeconds).atZone(pacificZone).format(timeFormatter)
}
