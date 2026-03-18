package com.caltrainnow.data.weather

import com.caltrainnow.core.model.WeatherInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Fetches daily weather data from Open-Meteo (open-source, no API key required).
 * Returns today's weather code, high, and low for a given lat/lng.
 */
@Singleton
class WeatherService @Inject constructor(
    private val httpClient: OkHttpClient
) {
    suspend fun fetchWeather(lat: Double, lng: Double): WeatherInfo? =
        withContext(Dispatchers.IO) {
            try {
                val url = "https://api.open-meteo.com/v1/forecast" +
                    "?latitude=$lat&longitude=$lng" +
                    "&daily=weather_code,temperature_2m_max,temperature_2m_min" +
                    "&temperature_unit=fahrenheit" +
                    "&timezone=America%2FLos_Angeles" +
                    "&forecast_days=1"

                val request = Request.Builder().url(url).build()
                val response = httpClient.newCall(request).execute()

                if (!response.isSuccessful) return@withContext null

                val body = response.body?.string() ?: return@withContext null
                val daily = JSONObject(body).getJSONObject("daily")

                WeatherInfo(
                    weatherCode = daily.getJSONArray("weather_code").getInt(0),
                    tempHighF = daily.getJSONArray("temperature_2m_max").getDouble(0),
                    tempLowF = daily.getJSONArray("temperature_2m_min").getDouble(0)
                )
            } catch (e: Exception) {
                null
            }
        }
}
