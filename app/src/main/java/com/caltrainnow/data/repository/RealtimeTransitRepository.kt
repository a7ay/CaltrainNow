package com.caltrainnow.data.repository

import com.caltrainnow.core.engine.RealtimeMatcher
import com.caltrainnow.core.model.Direction
import com.caltrainnow.core.model.RealtimeTransitResult
import com.caltrainnow.core.model.TrainDeparture
import com.caltrainnow.data.alerts.GtfsRealtimeMapper
import com.caltrainnow.data.alerts.RealtimeFeedCache
import com.caltrainnow.data.alerts.Transit511Api
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.CancellationException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RealtimeTransitRepository @Inject constructor(
    private val api: Transit511Api,
    private val cache: RealtimeFeedCache
) {
    companion object {
        const val MIN_REFRESH_INTERVAL_MILLIS = 15 * 60 * 1000L
    }

    suspend fun getRealtime(
        trains: List<TrainDeparture>,
        direction: Direction,
        nowMillis: Long = System.currentTimeMillis(),
        forceRefresh: Boolean = false
    ): RealtimeTransitResult {
        if (!api.isConfigured) {
            return RealtimeTransitResult(isConfigured = false)
        }

        var feeds = cache.read()
        var hadError = false

        if (forceRefresh || isRefreshDue(feeds.lastAttemptAt, nowMillis)) {
            cache.recordAttempt(nowMillis)
            val (alertsResult, tripsResult) = coroutineScope {
                val alerts = async { captureFailure { api.fetchServiceAlerts() } }
                val trips = async { captureFailure { api.fetchTripUpdates() } }
                alerts.await() to trips.await()
            }

            alertsResult.onSuccess { cache.saveServiceAlerts(it, nowMillis) }
                .onFailure { hadError = true }
            tripsResult.onSuccess { cache.saveTripUpdates(it, nowMillis) }
                .onFailure { hadError = true }
            feeds = cache.read()
        }

        val alerts = feeds.serviceAlerts?.let { feed ->
            runCatching { GtfsRealtimeMapper.toAlerts(feed) }.getOrElse {
                hadError = true
                emptyList()
            }
        }.orEmpty()
        val realtimeTrips = feeds.tripUpdates?.let { feed ->
            runCatching { GtfsRealtimeMapper.toTripUpdates(feed) }.getOrElse {
                hadError = true
                emptyList()
            }
        }.orEmpty()
        val lastAttemptFailed = feeds.lastAttemptAt != null && (
            feeds.serviceAlertsUpdatedAt == null || feeds.serviceAlertsUpdatedAt < feeds.lastAttemptAt ||
                feeds.tripUpdatesUpdatedAt == null || feeds.tripUpdatesUpdatedAt < feeds.lastAttemptAt
            )

        return RealtimeTransitResult(
            alerts = RealtimeMatcher.relevantAlerts(
                alerts = alerts,
                direction = direction,
                trains = trains,
                nowEpochSeconds = nowMillis / 1000
            ),
            trainUpdates = RealtimeMatcher.trainUpdates(trains, realtimeTrips),
            alertsUpdatedAtEpochMillis = feeds.serviceAlertsUpdatedAt,
            tripUpdatesUpdatedAtEpochMillis = feeds.tripUpdatesUpdatedAt,
            isConfigured = api.isConfigured,
            hasError = hadError || lastAttemptFailed
        )
    }

    private fun isRefreshDue(lastAttemptAt: Long?, nowMillis: Long): Boolean =
        lastAttemptAt == null || nowMillis - lastAttemptAt >= MIN_REFRESH_INTERVAL_MILLIS

    private suspend fun <T> captureFailure(block: suspend () -> T): Result<T> = try {
        Result.success(block())
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Exception) {
        Result.failure(error)
    }
}
