package com.caltrainnow.ui.home.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.caltrainnow.core.model.TrainDeparture
import com.caltrainnow.ui.theme.*

@Composable
fun TrainCard(
    train: TrainDeparture,
    onNavigate: () -> Unit,
    modifier: Modifier = Modifier
) {
    val routeColor = routeTypeColor(train.routeType)
    val countdownColor = when {
        train.minutesUntilDeparture <= 2 -> LateRed
        train.minutesUntilDeparture <= 5 -> WarningAmber
        else -> OnTimeGreen
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        shape = MaterialTheme.shapes.large
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .drawBehind {
                    // Left accent strip
                    drawRect(
                        color = routeColor,
                        topLeft = Offset.Zero,
                        size = Size(4.dp.toPx(), size.height)
                    )
                }
                .padding(start = 8.dp, end = 16.dp, top = 16.dp, bottom = 16.dp)
        ) {
            // Top row: time + countdown
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                // Train icon + time + badges
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("🚆", style = MaterialTheme.typography.headlineMedium)

                    Column {
                        Text(
                            text = train.departureTime,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Route type badge
                            Surface(
                                color = routeColor.copy(alpha = 0.12f),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    text = train.routeType,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = routeColor,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                )
                            }

                            // Train number
                            Text(
                                text = "Train #${train.trainNumber}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // Countdown chip
                Surface(
                    color = countdownColor.copy(alpha = 0.12f),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(
                        text = "${train.minutesUntilDeparture} min",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.ExtraBold,
                        color = countdownColor,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            }

            // Arrival time at destination
            if (train.arrivalTimeAtDestination != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(modifier = Modifier.padding(start = 42.dp)) {
                    Text(
                        text = "Arrives ${train.headsign}: ",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = train.arrivalTimeAtDestination,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            // Navigate button
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedButton(
                onClick = onNavigate,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(
                    1.5.dp,
                    MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                ),
                contentPadding = PaddingValues(vertical = 10.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Navigation,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Navigate to Station",
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
    }
}
