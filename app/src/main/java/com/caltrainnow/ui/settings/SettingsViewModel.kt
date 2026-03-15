package com.caltrainnow.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.caltrainnow.core.model.ScheduleMetadata
import com.caltrainnow.core.model.Station
import com.caltrainnow.data.location.LocationProvider
import com.caltrainnow.data.preferences.UserPrefsStore
import com.caltrainnow.data.repository.CaltrainRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val homeLabel: String = "",
    val homeLat: Double = 0.0,
    val homeLng: Double = 0.0,
    val workLabel: String = "",
    val workLat: Double = 0.0,
    val workLng: Double = 0.0,
    val stations: List<Station> = emptyList(),
    val scheduleMetadata: ScheduleMetadata? = null,
    val isDownloading: Boolean = false,
    val downloadResult: String? = null,
    val isSavingHome: Boolean = false,
    val isSavingWork: Boolean = false
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val prefsStore: UserPrefsStore,
    private val repository: CaltrainRepository,
    private val locationProvider: LocationProvider
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        // Observe user prefs
        viewModelScope.launch {
            prefsStore.userConfigFlow.collect { config ->
                _uiState.update {
                    it.copy(
                        homeLabel = config.homeLabel,
                        homeLat = config.homeLatitude,
                        homeLng = config.homeLongitude,
                        workLabel = config.workLabel,
                        workLat = config.workLatitude,
                        workLng = config.workLongitude
                    )
                }
            }
        }

        // Load schedule metadata and stations
        viewModelScope.launch {
            loadStations()
        }
    }

    private suspend fun loadStations() {
        val meta = repository.getScheduleMetadata()
        val allStations = repository.getAllStations()
        
        // Filter to parent stations only (locationType == 1)
        // Sort by latitude descending (North to South) to match the physical train line order
        val stationList = allStations
            .filter { it.locationType == 1 }
            .sortedByDescending { it.latitude }
        
        _uiState.update { 
            it.copy(
                scheduleMetadata = meta,
                stations = stationList
            ) 
        }
    }

    /**
     * Set home location to current GPS coordinates.
     */
    fun useCurrentLocationAsHome() {
        viewModelScope.launch {
            _uiState.update { it.copy(isSavingHome = true) }
            try {
                val loc = locationProvider.getCurrentLocation()
                prefsStore.setHome(loc.latitude, loc.longitude, "Current Location")
                _uiState.update { it.copy(isSavingHome = false) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isSavingHome = false, downloadResult = "Location error: ${e.message}") }
            }
        }
    }

    /**
     * Set work location to current GPS coordinates.
     */
    fun useCurrentLocationAsWork() {
        viewModelScope.launch {
            _uiState.update { it.copy(isSavingWork = true) }
            try {
                val loc = locationProvider.getCurrentLocation()
                prefsStore.setWork(loc.latitude, loc.longitude, "Current Location")
                _uiState.update { it.copy(isSavingWork = false) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isSavingWork = false, downloadResult = "Location error: ${e.message}") }
            }
        }
    }

    /**
     * Update home location by selecting a station.
     */
    fun setHomeStation(station: Station) {
        viewModelScope.launch {
            prefsStore.setHome(station.latitude, station.longitude, station.stationName)
        }
    }

    /**
     * Update work location by selecting a station.
     */
    fun setWorkStation(station: Station) {
        viewModelScope.launch {
            prefsStore.setWork(station.latitude, station.longitude, station.stationName)
        }
    }

    /**
     * Update home label manually.
     */
    fun updateHomeLabel(label: String) {
        viewModelScope.launch {
            prefsStore.setHome(_uiState.value.homeLat, _uiState.value.homeLng, label)
        }
    }

    /**
     * Update work label manually.
     */
    fun updateWorkLabel(label: String) {
        viewModelScope.launch {
            prefsStore.setWork(_uiState.value.workLat, _uiState.value.workLng, label)
        }
    }

    /**
     * Re-download the GTFS schedule.
     */
    fun redownloadSchedule() {
        viewModelScope.launch {
            _uiState.update { it.copy(isDownloading = true, downloadResult = null) }
            try {
                val result = repository.initialize()
                loadStations()
                val meta = repository.getScheduleMetadata()
                
                _uiState.update {
                    it.copy(
                        isDownloading = false,
                        scheduleMetadata = meta,
                        downloadResult = if (result.success) {
                            "Schedule updated: ${result.stationCount} stations, ${result.tripCount} trips"
                        } else {
                            result.errorMessage ?: "Download failed"
                        }
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isDownloading = false, downloadResult = "Error: ${e.message}")
                }
            }
        }
    }

    fun clearDownloadResult() {
        _uiState.update { it.copy(downloadResult = null) }
    }
}
