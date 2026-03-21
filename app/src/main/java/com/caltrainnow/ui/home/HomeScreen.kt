package com.caltrainnow.ui.home

import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import com.caltrainnow.core.model.WeatherInfo
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.caltrainnow.ui.home.components.*
import com.caltrainnow.ui.theme.CaltrainRed
import com.caltrainnow.util.NavigationUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onNavigateToSettings: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = "CaltrainNow",
                        style = MaterialTheme.typography.headlineMedium
                    )
                },
                actions = {
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Settings"
                        )
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    ) { padding ->

        when {
            // No schedule loaded
            !uiState.scheduleLoaded && !uiState.isLoading -> {
                NoScheduleContent(
                    isDownloading = uiState.isDownloadingSchedule,
                    progress = uiState.downloadProgress,
                    error = uiState.error,
                    onDownload = { viewModel.downloadSchedule() },
                    modifier = Modifier.padding(padding)
                )
            }

            // Location permission needed
            uiState.locationPermissionNeeded -> {
                LocationPermissionContent(
                    onPermissionGranted = { viewModel.onLocationPermissionGranted() },
                    modifier = Modifier.padding(padding)
                )
            }

            // Main content with pull-to-refresh
            else -> {
                PullToRefreshBox(
                    isRefreshing = uiState.isLoading,
                    onRefresh = { viewModel.refresh() },
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Error banner
                        if (uiState.error != null) {
                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.errorContainer
                                ),
                                shape = MaterialTheme.shapes.large
                            ) {
                                Column(
                                    modifier = Modifier.padding(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = uiState.error!!,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                    FilledTonalButton(onClick = { viewModel.refresh() }) {
                                        Text("Try Again")
                                    }
                                }
                            }
                        }

                        // Station banner (departure + departure weather)
                        if (uiState.nearestStation != null) {
                            StationBanner(
                                station = uiState.nearestStation!!,
                                distanceText = uiState.stationDistanceText,
                                departureWeather = uiState.departureWeather,
                                onNavigate = {
                                    val station = uiState.nearestStation!!
                                    val intent = NavigationUtils.getNavigationIntent(
                                        station.latitude,
                                        station.longitude,
                                        mode = NavigationUtils.TravelMode.DRIVING
                                    )
                                    try {
                                        context.startActivity(intent)
                                    } catch (e: Exception) {
                                        val browserIntent = NavigationUtils.getDirectionsIntent(
                                            station.latitude,
                                            station.longitude,
                                            station.name,
                                            mode = NavigationUtils.TravelMode.DRIVING
                                        )
                                        context.startActivity(browserIntent)
                                    }
                                }
                            )
                        }

                        // Direction row + destination weather
                        if (uiState.nearestStation != null) {
                            DirectionRow(
                                direction = uiState.direction,
                                reason = uiState.directionReason,
                                isOverridden = uiState.isDirectionOverridden,
                                onToggle = { viewModel.toggleDirection() }
                            )
                            if (uiState.destinationStation != null && uiState.destinationWeather != null) {
                                DestinationWeatherLine(
                                    stationName = uiState.destinationStation!!.name,
                                    weather = uiState.destinationWeather!!
                                )
                            }
                        }

                        // Train cards
                        if (uiState.nextTrains.isNotEmpty()) {
                            uiState.nextTrains.forEach { train ->
                                TrainCard(train = train)
                            }
                        } else if (!uiState.isLoading && uiState.error == null) {
                            // No trains
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                                ),
                                shape = MaterialTheme.shapes.large
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(32.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text("🌙", style = MaterialTheme.typography.headlineLarge)
                                    Text(
                                        text = "No more trains today",
                                        style = MaterialTheme.typography.titleLarge,
                                        textAlign = TextAlign.Center
                                    )
                                    Text(
                                        text = "Pull down to refresh",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        }

                        // Footer
                        if (uiState.lastRefreshed.isNotBlank()) {
                            Text(
                                text = "Last refreshed: ${uiState.lastRefreshed} · Pull down to refresh",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 4.dp, bottom = 16.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

// ── Sub-screens for special states ──────────────────────────────

@Composable
private fun NoScheduleContent(
    isDownloading: Boolean,
    progress: String?,
    error: String?,
    onDownload: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("🚆", style = MaterialTheme.typography.headlineLarge)
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Welcome to CaltrainNow",
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Download the Caltrain schedule to get started.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(24.dp))

        if (isDownloading) {
            CircularProgressIndicator()
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = progress ?: "Downloading...",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            Button(
                onClick = onDownload,
                modifier = Modifier.fillMaxWidth(0.7f),
                contentPadding = PaddingValues(vertical = 14.dp)
            ) {
                Text("Download Schedule", style = MaterialTheme.typography.labelLarge)
            }
        }

        if (error != null) {
            Spacer(modifier = Modifier.height(16.dp))
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                )
            ) {
                Text(
                    text = error,
                    modifier = Modifier.padding(12.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        }
    }
}

@Composable
private fun LocationPermissionContent(
    onPermissionGranted: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("📍", style = MaterialTheme.typography.headlineLarge)
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Location Needed",
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "CaltrainNow needs your location to find the nearest station and detect your travel direction.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(24.dp))

        // Note: Actual permission request is handled in MainActivity.
        // This button signals the ViewModel that the user wants to grant permission.
        Button(
            onClick = onPermissionGranted,
            modifier = Modifier.fillMaxWidth(0.7f),
            contentPadding = PaddingValues(vertical = 14.dp)
        ) {
            Text("Grant Location Permission", style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun DestinationWeatherLine(
    stationName: String,
    weather: WeatherInfo,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = weather.weatherEmoji(),
            style = MaterialTheme.typography.bodyMedium
        )
        Text(
            text = stationName,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = "↑${weather.tempHighF.toInt()}° ↓${weather.tempLowF.toInt()}°F",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
