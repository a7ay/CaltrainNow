package com.caltrainnow.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.caltrainnow.core.engine.DirectionResolver
import com.caltrainnow.core.model.*
import com.caltrainnow.data.location.LocationProvider
import com.caltrainnow.data.preferences.UserPrefsStore
import com.caltrainnow.data.preferences.WeatherCache
import com.caltrainnow.data.repository.CaltrainRepository
import com.caltrainnow.data.weather.WeatherService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject

data class HomeUiState(
    val isLoading: Boolean = true,
    val nearestStation: StationInfo? = null,
    val destinationStation: StationInfo? = null,
    val stationDistanceText: String = "",
    val direction: Direction = Direction.NORTHBOUND,
    val directionReason: String = "",
    val isDirectionOverridden: Boolean = false,
    val nextTrains: List<TrainDeparture> = emptyList(),
    val lastRefreshed: String = "",
    val error: String? = null,
    val scheduleLoaded: Boolean = false,
    val locationPermissionNeeded: Boolean = false,
    val isDownloadingSchedule: Boolean = false,
    val downloadProgress: String? = null,
    val departureWeather: WeatherInfo? = null,
    val destinationWeather: WeatherInfo? = null
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val repository: CaltrainRepository,
    private val locationProvider: LocationProvider,
    private val userPrefsStore: UserPrefsStore,
    private val directionResolver: DirectionResolver,
    private val weatherService: WeatherService,
    private val weatherCache: WeatherCache
) : ViewModel() {

    companion object {
        private val PT_ZONE = ZoneId.of("America/Los_Angeles")
        private val TIME_FORMATTER = DateTimeFormatter.ofPattern("h:mm a")
    }

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    // Cache the last GPS location for direction toggle (no re-fetch needed)
    private var lastLat: Double? = null
    private var lastLng: Double? = null

    init {
        // Observe user prefs and update DirectionResolver when they change
        viewModelScope.launch {
            userPrefsStore.userConfigFlow.collect { config ->
                directionResolver.userConfig = config
            }
        }

        checkScheduleAndLoad()
    }

    private fun checkScheduleAndLoad() {
        viewModelScope.launch {
            try {
                val loaded = repository.isScheduleLoaded()
                _uiState.update { it.copy(scheduleLoaded = loaded) }
                if (loaded) {
                    refresh()
                } else {
                    _uiState.update { it.copy(isLoading = false) }
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isLoading = false, error = "Failed to check schedule: ${e.message}")
                }
            }
        }
    }

    /**
     * Pull-to-refresh: re-fetch location, reset direction override, look up trains.
     */
    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null, isDirectionOverridden = false) }

            val location = getLocation() ?: return@launch
            lastLat = location.latitude
            lastLng = location.longitude

            lookupTrains(location.latitude, location.longitude, directionOverride = null)
        }
    }

    /**
     * Toggle direction: flip NB↔SB using cached location.
     */
    fun toggleDirection() {
        val lat = lastLat ?: return
        val lng = lastLng ?: return
        val currentDir = _uiState.value.direction
        val newDir = if (currentDir == Direction.NORTHBOUND) Direction.SOUTHBOUND else Direction.NORTHBOUND

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            lookupTrains(lat, lng, directionOverride = newDir)
        }
    }

    /**
     * Download GTFS schedule (first launch or manual refresh).
     */
    fun downloadSchedule() {
        viewModelScope.launch {
            _uiState.update {
                it.copy(isDownloadingSchedule = true, downloadProgress = "Downloading schedule...", error = null)
            }
            try {
                val result = repository.initialize()
                if (result.success) {
                    _uiState.update {
                        it.copy(
                            isDownloadingSchedule = false,
                            downloadProgress = null,
                            scheduleLoaded = true
                        )
                    }
                    refresh()
                } else {
                    val msg = result.errorMessage
                        ?: result.validationResult?.errors?.joinToString("\n")
                        ?: "Schedule download failed"
                    _uiState.update {
                        it.copy(isDownloadingSchedule = false, downloadProgress = null, error = msg)
                    }
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isDownloadingSchedule = false, downloadProgress = null, error = "Download failed: ${e.message}")
                }
            }
        }
    }

    /**
     * Called when location permission is granted.
     */
    fun onLocationPermissionGranted() {
        _uiState.update { it.copy(locationPermissionNeeded = false) }
        refresh()
    }

    // ── Internal helpers ─────────────────────────────────────

    /**
     * Get current GPS location. Updates UI state on failure.
     */
    private suspend fun getLocation(): LocationProvider.LatLng? {
        return try {
            locationProvider.getCurrentLocation()
        } catch (e: SecurityException) {
            _uiState.update { it.copy(isLoading = false, locationPermissionNeeded = true) }
            null
        } catch (e: Exception) {
            try {
                locationProvider.getLastKnownLocation()
            } catch (e2: Exception) {
                _uiState.update {
                    it.copy(isLoading = false, error = "Could not determine location. Please enable GPS.")
                }
                null
            }
        }
    }

    /**
     * Core lookup logic — shared by refresh() and toggleDirection().
     */
    private suspend fun lookupTrains(
        lat: Double,
        lng: Double,
        directionOverride: Direction?
    ) {
        try {
            val now = LocalDateTime.now(PT_ZONE)
            val result = if (directionOverride != null) {
                repository.lookupNextTrainsWithDirection(lat, lng, directionOverride, now)
            } else {
                repository.lookupNextTrains(lat, lng, now)
            }

            val isOverride = directionOverride != null
            _uiState.update {
                it.copy(
                    isLoading = false,
                    nearestStation = result.nearestStation,
                    destinationStation = result.destinationStation,
                    stationDistanceText = formatDistance(result.stationDistanceMeters),
                    direction = directionOverride ?: result.direction,
                    directionReason = if (isOverride) "Manual override" else result.directionReason,
                    isDirectionOverridden = isOverride,
                    nextTrains = result.nextTrains,
                    lastRefreshed = now.format(TIME_FORMATTER),
                    error = null,
                    scheduleLoaded = true,
                    locationPermissionNeeded = false
                )
            }

            // Fetch weather for both stations without blocking train display
            val dep = result.nearestStation
            val dest = result.destinationStation
            viewModelScope.launch {
                val weather = getWeatherCached(dep.latitude, dep.longitude, isDeparture = true)
                _uiState.update { it.copy(departureWeather = weather) }
            }
            if (dest != null) {
                viewModelScope.launch {
                    val weather = getWeatherCached(dest.latitude, dest.longitude, isDeparture = false)
                    _uiState.update { it.copy(destinationWeather = weather) }
                }
            }
        } catch (e: Exception) {
            _uiState.update {
                it.copy(isLoading = false, error = e.message ?: "Lookup failed")
            }
        }
    }

    private suspend fun getWeatherCached(lat: Double, lng: Double, isDeparture: Boolean): WeatherInfo? {
        val cached = if (isDeparture) weatherCache.getDeparture(lat, lng)
                     else weatherCache.getDestination(lat, lng)
        if (cached != null) return cached

        val fresh = weatherService.fetchWeather(lat, lng) ?: return null
        if (isDeparture) weatherCache.saveDeparture(lat, lng, fresh)
        else weatherCache.saveDestination(lat, lng, fresh)
        return fresh
    }

    private fun formatDistance(meters: Double): String {
        val miles = meters / 1609.34
        return if (miles < 0.1) {
            "${meters.toInt()} ft"
        } else {
            "%.1f mi".format(miles)
        }
    }
}
