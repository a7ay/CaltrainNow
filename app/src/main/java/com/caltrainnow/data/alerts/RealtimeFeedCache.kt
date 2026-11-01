package com.caltrainnow.data.alerts

import android.content.Context
import android.util.Base64
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.google.transit.realtime.GtfsRealtime
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

private val Context.realtimeDataStore by preferencesDataStore(name = "realtime_transit_cache")

data class CachedRealtimeFeeds(
    val serviceAlerts: GtfsRealtime.FeedMessage?,
    val tripUpdates: GtfsRealtime.FeedMessage?,
    val serviceAlertsUpdatedAt: Long?,
    val tripUpdatesUpdatedAt: Long?,
    val lastAttemptAt: Long?
)

@Singleton
class RealtimeFeedCache @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private companion object {
        val ALERTS_PAYLOAD = stringPreferencesKey("service_alerts_proto_base64")
        val TRIPS_PAYLOAD = stringPreferencesKey("trip_updates_proto_base64")
        val ALERTS_UPDATED_AT = longPreferencesKey("service_alerts_updated_at")
        val TRIPS_UPDATED_AT = longPreferencesKey("trip_updates_updated_at")
        val LAST_ATTEMPT_AT = longPreferencesKey("last_attempt_at")
    }

    suspend fun read(): CachedRealtimeFeeds {
        val prefs = context.realtimeDataStore.data.first()
        return CachedRealtimeFeeds(
            serviceAlerts = prefs[ALERTS_PAYLOAD]?.decodeFeed(),
            tripUpdates = prefs[TRIPS_PAYLOAD]?.decodeFeed(),
            serviceAlertsUpdatedAt = prefs[ALERTS_UPDATED_AT],
            tripUpdatesUpdatedAt = prefs[TRIPS_UPDATED_AT],
            lastAttemptAt = prefs[LAST_ATTEMPT_AT]
        )
    }

    suspend fun recordAttempt(atMillis: Long) {
        context.realtimeDataStore.edit { it[LAST_ATTEMPT_AT] = atMillis }
    }

    suspend fun saveServiceAlerts(feed: GtfsRealtime.FeedMessage, atMillis: Long) {
        context.realtimeDataStore.edit { prefs ->
            prefs[ALERTS_PAYLOAD] = feed.toByteArray().encodeBase64()
            prefs[ALERTS_UPDATED_AT] = atMillis
        }
    }

    suspend fun saveTripUpdates(feed: GtfsRealtime.FeedMessage, atMillis: Long) {
        context.realtimeDataStore.edit { prefs ->
            prefs[TRIPS_PAYLOAD] = feed.toByteArray().encodeBase64()
            prefs[TRIPS_UPDATED_AT] = atMillis
        }
    }

    private fun String.decodeFeed(): GtfsRealtime.FeedMessage? = try {
        GtfsRealtime.FeedMessage.parseFrom(Base64.decode(this, Base64.NO_WRAP))
    } catch (_: Exception) {
        null
    }

    private fun ByteArray.encodeBase64(): String = Base64.encodeToString(this, Base64.NO_WRAP)
}
