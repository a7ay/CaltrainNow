package com.caltrainnow.util

import android.content.Intent
import android.net.Uri

/**
 * Builds Google Maps navigation intents.
 * Uses Google Maps intents — no API key needed.
 */
object NavigationUtils {

    enum class TravelMode(val code: String) {
        WALKING("w"),
        DRIVING("d"),
        BICYCLING("b"),
        TRANSIT("r")
    }

    /**
     * Create an Intent that opens Google Maps with turn-by-turn navigation
     * to the specified destination.
     *
     * @param destinationLat Destination latitude
     * @param destinationLng Destination longitude
     * @param mode Travel mode (default: walking)
     * @return Intent to launch Google Maps navigation
     */
    fun getNavigationIntent(
        destinationLat: Double,
        destinationLng: Double,
        mode: TravelMode = TravelMode.WALKING
    ): Intent {
        val uri = Uri.parse(
            "google.navigation:q=$destinationLat,$destinationLng&mode=${mode.code}"
        )
        return Intent(Intent.ACTION_VIEW, uri).apply {
            setPackage("com.google.android.apps.maps")
        }
    }

    /**
     * Create an Intent that opens Google Maps showing directions
     * from the current location to the destination.
     *
     * @param destinationLat Destination latitude
     * @param destinationLng Destination longitude
     * @param destinationLabel Label for the destination (e.g., "Palo Alto Caltrain")
     * @param mode Travel mode (default: walking)
     * @return Intent to launch Google Maps directions
     */
    fun getDirectionsIntent(
        destinationLat: Double,
        destinationLng: Double,
        destinationLabel: String = "",
        mode: TravelMode = TravelMode.WALKING
    ): Intent {
        val modeParam = when (mode) {
            TravelMode.WALKING -> "walking"
            TravelMode.DRIVING -> "driving"
            TravelMode.BICYCLING -> "bicycling"
            TravelMode.TRANSIT -> "transit"
        }
        val label = if (destinationLabel.isNotBlank()) "($destinationLabel)" else ""
        val uri = Uri.parse(
            "https://www.google.com/maps/dir/?api=1" +
                "&destination=$destinationLat,$destinationLng$label" +
                "&travelmode=$modeParam"
        )
        return Intent(Intent.ACTION_VIEW, uri)
    }

    /**
     * Build a Google Maps URL string for embedding or sharing.
     */
    fun getGoogleMapsUrl(
        destinationLat: Double,
        destinationLng: Double,
        mode: TravelMode = TravelMode.WALKING
    ): String {
        val modeParam = when (mode) {
            TravelMode.WALKING -> "walking"
            TravelMode.DRIVING -> "driving"
            TravelMode.BICYCLING -> "bicycling"
            TravelMode.TRANSIT -> "transit"
        }
        return "https://www.google.com/maps/dir/?api=1" +
            "&destination=$destinationLat,$destinationLng" +
            "&travelmode=$modeParam"
    }
}
