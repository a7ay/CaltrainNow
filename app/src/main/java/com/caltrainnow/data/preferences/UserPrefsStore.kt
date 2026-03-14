package com.caltrainnow.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.caltrainnow.core.model.UserConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "user_prefs")

/**
 * Wraps DataStore for reading/writing home and work locations.
 * Provides a Flow<UserConfig> that the ViewModel observes.
 */
@Singleton
class UserPrefsStore @Inject constructor(
    private val context: Context
) {
    companion object {
        private val HOME_LAT = doublePreferencesKey("home_lat")
        private val HOME_LNG = doublePreferencesKey("home_lng")
        private val HOME_LABEL = stringPreferencesKey("home_label")
        private val WORK_LAT = doublePreferencesKey("work_lat")
        private val WORK_LNG = doublePreferencesKey("work_lng")
        private val WORK_LABEL = stringPreferencesKey("work_label")
    }

    /**
     * Observe user config as a Flow. Emits defaults if no data saved.
     */
    val userConfigFlow: Flow<UserConfig> = context.dataStore.data.map { prefs ->
        UserConfig(
            homeLatitude = prefs[HOME_LAT] ?: UserConfig.DEFAULT.homeLatitude,
            homeLongitude = prefs[HOME_LNG] ?: UserConfig.DEFAULT.homeLongitude,
            homeLabel = prefs[HOME_LABEL] ?: UserConfig.DEFAULT.homeLabel,
            workLatitude = prefs[WORK_LAT] ?: UserConfig.DEFAULT.workLatitude,
            workLongitude = prefs[WORK_LNG] ?: UserConfig.DEFAULT.workLongitude,
            workLabel = prefs[WORK_LABEL] ?: UserConfig.DEFAULT.workLabel
        )
    }

    /**
     * Save home location.
     */
    suspend fun setHome(lat: Double, lng: Double, label: String) {
        context.dataStore.edit { prefs ->
            prefs[HOME_LAT] = lat
            prefs[HOME_LNG] = lng
            prefs[HOME_LABEL] = label
        }
    }

    /**
     * Save work location.
     */
    suspend fun setWork(lat: Double, lng: Double, label: String) {
        context.dataStore.edit { prefs ->
            prefs[WORK_LAT] = lat
            prefs[WORK_LNG] = lng
            prefs[WORK_LABEL] = label
        }
    }
}
