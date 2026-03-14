package com.caltrainnow.ui.theme

import androidx.compose.ui.graphics.Color

// ── Caltrain-inspired palette (fallback for pre-Android 12) ────────

val CaltrainRed = Color(0xFFE31837)
val CaltrainRedDark = Color(0xFFC41230)
val CaltrainNavy = Color(0xFF1A1A2E)
val CaltrainNavyLight = Color(0xFF2D2D44)

val OnTimeGreen = Color(0xFF4CAF50)
val WarningAmber = Color(0xFFFFA726)
val LateRed = Color(0xFFEF5350)

// Route type colors
val LocalBlue = Color(0xFF2196F3)
val LimitedPurple = Color(0xFF7C4DFF)
val ExpressRed = Color(0xFFE31837)
val WeekendTeal = Color(0xFF26A69A)

// Light scheme fallback
val md_theme_light_primary = CaltrainRed
val md_theme_light_onPrimary = Color.White
val md_theme_light_primaryContainer = Color(0xFFFFDAD6)
val md_theme_light_onPrimaryContainer = Color(0xFF410003)
val md_theme_light_secondary = CaltrainNavy
val md_theme_light_onSecondary = Color.White
val md_theme_light_secondaryContainer = Color(0xFFDDE1F9)
val md_theme_light_onSecondaryContainer = Color(0xFF141B2E)
val md_theme_light_tertiary = OnTimeGreen
val md_theme_light_onTertiary = Color.White
val md_theme_light_tertiaryContainer = Color(0xFFC8E6C9)
val md_theme_light_onTertiaryContainer = Color(0xFF002106)
val md_theme_light_background = Color(0xFFFFFBFF)
val md_theme_light_onBackground = Color(0xFF201A19)
val md_theme_light_surface = Color(0xFFFFFBFF)
val md_theme_light_onSurface = Color(0xFF201A19)
val md_theme_light_surfaceVariant = Color(0xFFF5DDDA)
val md_theme_light_onSurfaceVariant = Color(0xFF534341)
val md_theme_light_error = Color(0xFFBA1A1A)
val md_theme_light_onError = Color.White
val md_theme_light_outline = Color(0xFF857370)

// Dark scheme fallback
val md_theme_dark_primary = Color(0xFFFFB4AB)
val md_theme_dark_onPrimary = Color(0xFF690007)
val md_theme_dark_primaryContainer = CaltrainRedDark
val md_theme_dark_onPrimaryContainer = Color(0xFFFFDAD6)
val md_theme_dark_secondary = Color(0xFFC0C5DD)
val md_theme_dark_onSecondary = Color(0xFF293043)
val md_theme_dark_secondaryContainer = CaltrainNavyLight
val md_theme_dark_onSecondaryContainer = Color(0xFFDDE1F9)
val md_theme_dark_tertiary = Color(0xFFA5D6A7)
val md_theme_dark_onTertiary = Color(0xFF003910)
val md_theme_dark_tertiaryContainer = Color(0xFF2E7D32)
val md_theme_dark_onTertiaryContainer = Color(0xFFC8E6C9)
val md_theme_dark_background = Color(0xFF201A19)
val md_theme_dark_onBackground = Color(0xFFEDE0DE)
val md_theme_dark_surface = Color(0xFF201A19)
val md_theme_dark_onSurface = Color(0xFFEDE0DE)
val md_theme_dark_surfaceVariant = Color(0xFF534341)
val md_theme_dark_onSurfaceVariant = Color(0xFFD8C2BF)
val md_theme_dark_error = Color(0xFFFFB4AB)
val md_theme_dark_onError = Color(0xFF690007)
val md_theme_dark_outline = Color(0xFFA08C8A)

/**
 * Get route type color for a given route name.
 */
fun routeTypeColor(routeType: String): Color {
    return when {
        routeType.contains("Express", ignoreCase = true) -> ExpressRed
        routeType.contains("Limited", ignoreCase = true) -> LimitedPurple
        routeType.contains("Weekend", ignoreCase = true) -> WeekendTeal
        else -> LocalBlue
    }
}
