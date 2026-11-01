package com.caltrainnow.data.alerts

import com.google.transit.realtime.GtfsRealtime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

class Transit511Api(
    private val httpClient: OkHttpClient,
    apiKey: String
) {
    private val apiKey = apiKey.trim()

    companion object {
        // Confirmed by the 511 operator documentation (Caltrain = CT).
        private const val CALTRAIN_AGENCY_ID = "CT"
    }

    val isConfigured: Boolean get() = apiKey.isNotBlank()

    suspend fun fetchServiceAlerts(): GtfsRealtime.FeedMessage = withContext(Dispatchers.IO) {
        val url = endpoint("servicealerts")
            .addQueryParameter("format", "json")
            .build()
        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/json")
            .build()

        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("511 service alerts HTTP ${response.code}")
            val body = response.body?.string() ?: throw IOException("511 service alerts response was empty")
            GtfsRealtimeMapper.parseServiceAlertsJson(body)
        }
    }

    suspend fun fetchTripUpdates(): GtfsRealtime.FeedMessage = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(endpoint("tripupdates").build())
            .header("Accept", "application/x-protobuf")
            .build()

        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("511 trip updates HTTP ${response.code}")
            val body = response.body?.bytes() ?: throw IOException("511 trip updates response was empty")
            GtfsRealtimeMapper.parseTripUpdates(body)
        }
    }

    private fun endpoint(resource: String): HttpUrl.Builder = HttpUrl.Builder()
        .scheme("https")
        .host("api.511.org")
        .addPathSegment("transit")
        .addPathSegment(resource)
        .addQueryParameter("api_key", apiKey)
        .addQueryParameter("agency", CALTRAIN_AGENCY_ID)
}
