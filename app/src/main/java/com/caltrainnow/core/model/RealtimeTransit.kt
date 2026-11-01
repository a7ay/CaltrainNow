package com.caltrainnow.core.model

data class AlertPeriod(
    val startEpochSeconds: Long?,
    val endEpochSeconds: Long?
)

data class AlertEntitySelector(
    val routeId: String? = null,
    val stopId: String? = null,
    val directionId: Int? = null,
    val tripId: String? = null
) {
    val isUnrestricted: Boolean
        get() = routeId == null && stopId == null && directionId == null && tripId == null
}

data class TransitAlert(
    val id: String,
    val title: String,
    val description: String?,
    val url: String?,
    val severity: String?,
    val periods: List<AlertPeriod>,
    val informedEntities: List<AlertEntitySelector>
)

enum class TrainRealtimeStatus {
    NO_UPDATE,
    ON_TIME,
    DELAYED,
    CANCELED,
    UPDATED
}

data class TrainRealtimeUpdate(
    val tripId: String,
    val status: TrainRealtimeStatus,
    val delayMinutes: Int? = null,
    val updatedDepartureTime: String? = null,
    val updatedArrivalTime: String? = null
)

data class RealtimeTransitResult(
    val alerts: List<TransitAlert> = emptyList(),
    val trainUpdates: Map<String, TrainRealtimeUpdate> = emptyMap(),
    val alertsUpdatedAtEpochMillis: Long? = null,
    val tripUpdatesUpdatedAtEpochMillis: Long? = null,
    val isConfigured: Boolean = false,
    val hasError: Boolean = false
)
