package com.caltrainnow.ui.home.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.caltrainnow.core.model.StationInfo
import com.caltrainnow.core.model.WeatherInfo

@Composable
fun StationBanner(
    station: StationInfo,
    distanceText: String,
    onNavigate: () -> Unit,
    departureWeather: WeatherInfo? = null,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        shape = MaterialTheme.shapes.large
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left: icon + station name + label
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(42.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text("📍", style = MaterialTheme.typography.titleMedium)
                    }
                }
                Column {
                    Text(
                        text = station.name,
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Nearest station",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Right: weather (if loaded) + distance/drive time
            Column(horizontalAlignment = Alignment.End, modifier = Modifier.padding(end = 4.dp)) {
                if (departureWeather != null) {
                    Text(
                        text = "${departureWeather.weatherEmoji()}  ↑${departureWeather.tempHighF.toInt()}° ↓${departureWeather.tempLowF.toInt()}°F",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = distanceText,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Text(
                        text = distanceText,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Bold
                    )
                }
                val driveMinutes = estimateDriveMinutes(distanceText)
                if (driveMinutes != null) {
                    Text(
                        text = "$driveMinutes min drive",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Navigate icon button
            FilledTonalIconButton(onClick = onNavigate) {
                Icon(
                    imageVector = Icons.Default.Navigation,
                    contentDescription = "Navigate to station"
                )
            }
        }
    }
}

/**
 * Rough estimate of driving time from distance text.
 * Average driving speed: ~25-30 mph in city/commute or ~2 min/mile.
 */
private fun estimateDriveMinutes(distanceText: String): Int? {
    return try {
        val value = distanceText.replace(Regex("[^0-9.]"), "").toDouble()
        when {
            distanceText.contains("mi") -> {
                val minutes = (value * 2.0).toInt()
                if (minutes < 1) 1 else minutes
            }
            distanceText.contains("ft") -> 1
            else -> null
        }
    } catch (e: Exception) {
        null
    }
}
