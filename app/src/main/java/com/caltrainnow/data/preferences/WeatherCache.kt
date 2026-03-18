package com.caltrainnow.data.preferences

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.caltrainnow.core.model.WeatherInfo
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

private val Context.weatherDataStore by preferencesDataStore(name = "weather_cache")

/**
 * Persists daily weather for departure and destination stations.
 * Returns cached data if it was fetched today for the same station; otherwise null (fetch needed).
 */
@Singleton
class WeatherCache @Inject constructor(
    @ApplicationContext private val context: Context
) {
    // Departure keys
    private val DEP_DATE = stringPreferencesKey("dep_date")
    private val DEP_KEY  = stringPreferencesKey("dep_key")
    private val DEP_CODE = intPreferencesKey("dep_code")
    private val DEP_HIGH = floatPreferencesKey("dep_high")
    private val DEP_LOW  = floatPreferencesKey("dep_low")

    // Destination keys
    private val DEST_DATE = stringPreferencesKey("dest_date")
    private val DEST_KEY  = stringPreferencesKey("dest_key")
    private val DEST_CODE = intPreferencesKey("dest_code")
    private val DEST_HIGH = floatPreferencesKey("dest_high")
    private val DEST_LOW  = floatPreferencesKey("dest_low")

    suspend fun getDeparture(lat: Double, lng: Double): WeatherInfo? {
        val prefs = context.weatherDataStore.data.first()
        val today = LocalDate.now().toString()
        if (prefs[DEP_DATE] != today || prefs[DEP_KEY] != stationKey(lat, lng)) return null
        val code = prefs[DEP_CODE] ?: return null
        val high = prefs[DEP_HIGH] ?: return null
        val low  = prefs[DEP_LOW]  ?: return null
        return WeatherInfo(code, high.toDouble(), low.toDouble())
    }

    suspend fun saveDeparture(lat: Double, lng: Double, weather: WeatherInfo) {
        context.weatherDataStore.edit { prefs ->
            prefs[DEP_DATE] = LocalDate.now().toString()
            prefs[DEP_KEY]  = stationKey(lat, lng)
            prefs[DEP_CODE] = weather.weatherCode
            prefs[DEP_HIGH] = weather.tempHighF.toFloat()
            prefs[DEP_LOW]  = weather.tempLowF.toFloat()
        }
    }

    suspend fun getDestination(lat: Double, lng: Double): WeatherInfo? {
        val prefs = context.weatherDataStore.data.first()
        val today = LocalDate.now().toString()
        if (prefs[DEST_DATE] != today || prefs[DEST_KEY] != stationKey(lat, lng)) return null
        val code = prefs[DEST_CODE] ?: return null
        val high = prefs[DEST_HIGH] ?: return null
        val low  = prefs[DEST_LOW]  ?: return null
        return WeatherInfo(code, high.toDouble(), low.toDouble())
    }

    suspend fun saveDestination(lat: Double, lng: Double, weather: WeatherInfo) {
        context.weatherDataStore.edit { prefs ->
            prefs[DEST_DATE] = LocalDate.now().toString()
            prefs[DEST_KEY]  = stationKey(lat, lng)
            prefs[DEST_CODE] = weather.weatherCode
            prefs[DEST_HIGH] = weather.tempHighF.toFloat()
            prefs[DEST_LOW]  = weather.tempLowF.toFloat()
        }
    }

    private fun stationKey(lat: Double, lng: Double) = "%.3f_%.3f".format(lat, lng)
}
