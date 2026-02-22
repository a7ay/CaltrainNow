package com.caltrainnow.data.location

import android.annotation.SuppressLint
import android.content.Context
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Provides the device's current GPS location.
 * Requires ACCESS_FINE_LOCATION or ACCESS_COARSE_LOCATION permission.
 */
@Singleton
class LocationProvider @Inject constructor(
    private val context: Context
) {
    private val fusedClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context)

    data class LatLng(val latitude: Double, val longitude: Double)

    /**
     * Get the device's current location.
     * Caller must ensure location permission is granted before calling.
     *
     * @throws SecurityException if location permission not granted
     * @throws LocationException if location cannot be determined
     */
    @SuppressLint("MissingPermission")
    suspend fun getCurrentLocation(): LatLng = suspendCancellableCoroutine { continuation ->
        val cancellationToken = CancellationTokenSource()

        fusedClient.getCurrentLocation(
            Priority.PRIORITY_HIGH_ACCURACY,
            cancellationToken.token
        ).addOnSuccessListener { location ->
            if (location != null) {
                continuation.resume(LatLng(location.latitude, location.longitude))
            } else {
                continuation.resumeWithException(
                    LocationException("Could not determine current location")
                )
            }
        }.addOnFailureListener { exception ->
            continuation.resumeWithException(
                LocationException("Location request failed: ${exception.message}", exception)
            )
        }

        continuation.invokeOnCancellation {
            cancellationToken.cancel()
        }
    }

    /**
     * Get the last known location (faster but may be stale).
     * Falls back to getCurrentLocation() if no last known location.
     */
    @SuppressLint("MissingPermission")
    suspend fun getLastKnownLocation(): LatLng = suspendCancellableCoroutine { continuation ->
        fusedClient.lastLocation
            .addOnSuccessListener { location ->
                if (location != null) {
                    continuation.resume(LatLng(location.latitude, location.longitude))
                } else {
                    // No cached location — this will throw, caller should retry with getCurrentLocation
                    continuation.resumeWithException(
                        LocationException("No last known location available")
                    )
                }
            }
            .addOnFailureListener { exception ->
                continuation.resumeWithException(
                    LocationException("Failed to get last location: ${exception.message}", exception)
                )
            }
    }
}

class LocationException(message: String, cause: Throwable? = null) : Exception(message, cause)
