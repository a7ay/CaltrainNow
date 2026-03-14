package com.caltrainnow.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.caltrainnow.core.model.Station
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ── Locations section ──────────────────────────────

            SectionHeader("LOCATIONS")

            LocationCard(
                icon = "🏠",
                label = "Home",
                name = uiState.homeLabel.ifBlank { "Not set" },
                lat = uiState.homeLat,
                lng = uiState.homeLng,
                stations = uiState.stations,
                isSaving = uiState.isSavingHome,
                onUseCurrent = { viewModel.useCurrentLocationAsHome() },
                onLabelChanged = { viewModel.updateHomeLabel(it) },
                onStationSelected = { viewModel.setHomeStation(it) }
            )

            LocationCard(
                icon = "🏢",
                label = "Work",
                name = uiState.workLabel.ifBlank { "Not set" },
                lat = uiState.workLat,
                lng = uiState.workLng,
                stations = uiState.stations,
                isSaving = uiState.isSavingWork,
                onUseCurrent = { viewModel.useCurrentLocationAsWork() },
                onLabelChanged = { viewModel.updateWorkLabel(it) },
                onStationSelected = { viewModel.setWorkStation(it) }
            )

            Spacer(modifier = Modifier.height(8.dp))

            // ── Schedule Data section ──────────────────────────

            SectionHeader("SCHEDULE DATA")

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val meta = uiState.scheduleMetadata
                    if (meta != null) {
                        InfoRow(
                            label = "Downloaded",
                            value = formatDownloadDate(meta.downloadedAt)
                        )
                        InfoRow(
                            label = "Data",
                            value = "${meta.stationCount} stations · ${meta.tripCount} trips"
                        )
                    } else {
                        Text(
                            text = "No schedule data loaded",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Button(
                        onClick = { viewModel.redownloadSchedule() },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !uiState.isDownloading,
                        contentPadding = PaddingValues(vertical = 12.dp)
                    ) {
                        if (uiState.isDownloading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Downloading...")
                        } else {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (meta != null) "Re-download Schedule" else "Download Schedule",
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                    }

                    // Download result snackbar
                    if (uiState.downloadResult != null) {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = if (uiState.downloadResult!!.startsWith("Error") ||
                                    uiState.downloadResult!!.startsWith("Download failed"))
                                    MaterialTheme.colorScheme.errorContainer
                                else
                                    MaterialTheme.colorScheme.tertiaryContainer
                            ),
                            shape = MaterialTheme.shapes.medium
                        ) {
                            Text(
                                text = uiState.downloadResult!!,
                                modifier = Modifier.padding(12.dp),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // ── About section ─────────────────────────────────

            SectionHeader("ABOUT")

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "CaltrainNow v1.0.0",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Schedule source: Caltrain GTFS",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                    Text(
                        text = "Made for Caltrain commuters",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

// ── Sub-components ──────────────────────────────────────────────

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 4.dp)
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LocationCard(
    icon: String,
    label: String,
    name: String,
    lat: Double,
    lng: Double,
    stations: List<Station>,
    isSaving: Boolean,
    onUseCurrent: () -> Unit,
    onLabelChanged: (String) -> Unit,
    onStationSelected: (Station) -> Unit
) {
    var isEditing by remember { mutableStateOf(false) }
    var expanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(icon, style = MaterialTheme.typography.headlineMedium)
                    Column {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        
                        if (isEditing) {
                            ExposedDropdownMenuBox(
                                expanded = expanded,
                                onExpandedChange = { expanded = it },
                                modifier = Modifier.padding(top = 8.dp)
                            ) {
                                OutlinedTextField(
                                    value = name,
                                    onValueChange = {},
                                    readOnly = true,
                                    label = { Text("Select Station") },
                                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                                    colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                                    modifier = Modifier.menuAnchor(),
                                    textStyle = MaterialTheme.typography.bodyMedium
                                )
                                ExposedDropdownMenu(
                                    expanded = expanded,
                                    onDismissRequest = { expanded = false }
                                ) {
                                    stations.forEach { station ->
                                        DropdownMenuItem(
                                            text = { Text(station.stationName) },
                                            onClick = {
                                                onStationSelected(station)
                                                expanded = false
                                                isEditing = false
                                            }
                                        )
                                    }
                                }
                            }
                        } else {
                            Text(
                                text = name,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 2.dp)
                            )
                        }
                        
                        if (!isEditing && (lat != 0.0 || lng != 0.0)) {
                            Text(
                                text = "%.4f, %.4f".format(lat, lng),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.outline,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier.padding(top = 2.dp)
                            )
                        }
                    }
                }

                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    FilledTonalButton(
                        onClick = onUseCurrent,
                        enabled = !isSaving,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        if (isSaving) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(14.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.MyLocation,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("GPS", style = MaterialTheme.typography.labelSmall)
                    }

                    TextButton(
                        onClick = { isEditing = !isEditing },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                    ) {
                        Text(
                            if (isEditing) "Cancel" else "Select Station",
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

private fun formatDownloadDate(isoDate: String): String {
    return try {
        val dt = LocalDateTime.parse(isoDate, DateTimeFormatter.ISO_LOCAL_DATE_TIME)
        dt.format(DateTimeFormatter.ofPattern("MMM d, yyyy"))
    } catch (e: Exception) {
        isoDate
    }
}
