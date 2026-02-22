package com.caltrainnow.core.model

/**
 * User's home and work locations for direction auto-detection.
 */
data class UserConfig(
    val homeLatitude: Double,
    val homeLongitude: Double,
    val homeLabel: String = "",
    val workLatitude: Double,
    val workLongitude: Double,
    val workLabel: String = ""
) {
    companion object {
        /**
         * Default config — Sunnyvale (home) to San Francisco (work).
         * User should update this via settings.
         */
        val DEFAULT = UserConfig(
            homeLatitude = 37.3688,
            homeLongitude = -122.0363,
            homeLabel = "Sunnyvale",
            workLatitude = 37.7764,
            workLongitude = -122.3943,
            workLabel = "San Francisco"
        )
    }
}
