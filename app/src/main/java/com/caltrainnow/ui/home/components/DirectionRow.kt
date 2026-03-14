package com.caltrainnow.ui.home.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.caltrainnow.core.model.Direction

@Composable
fun DirectionRow(
    direction: Direction,
    reason: String,
    isOverridden: Boolean,
    onToggle: () -> Unit,
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
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Segmented toggle
            Row(
                modifier = Modifier,
                horizontalArrangement = Arrangement.spacedBy(0.dp)
            ) {
                val isNB = direction == Direction.NORTHBOUND
                val selectedColor = MaterialTheme.colorScheme.primary
                val unselectedColor = MaterialTheme.colorScheme.surfaceVariant

                FilledTonalButton(
                    onClick = { if (!isNB) onToggle() },
                    shape = RoundedCornerShape(topStart = 12.dp, bottomStart = 12.dp, topEnd = 0.dp, bottomEnd = 0.dp),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = if (isNB) selectedColor else unselectedColor,
                        contentColor = if (isNB) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    modifier = Modifier.height(40.dp)
                ) {
                    Text("◀ NB", style = MaterialTheme.typography.labelLarge)
                }

                FilledTonalButton(
                    onClick = { if (isNB) onToggle() },
                    shape = RoundedCornerShape(topStart = 0.dp, bottomStart = 0.dp, topEnd = 12.dp, bottomEnd = 12.dp),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = if (!isNB) selectedColor else unselectedColor,
                        contentColor = if (!isNB) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    modifier = Modifier.height(40.dp)
                ) {
                    Text("SB ▶", style = MaterialTheme.typography.labelLarge)
                }
            }

            // Reason text
            Text(
                text = if (isOverridden) "Manual override" else reason,
                style = MaterialTheme.typography.bodySmall,
                color = if (isOverridden)
                    MaterialTheme.colorScheme.tertiary
                else
                    MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.End,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 12.dp)
            )
        }
    }
}
