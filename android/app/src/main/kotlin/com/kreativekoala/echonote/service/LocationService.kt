package com.kreativekoala.echonote.service

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.Locale
import javax.inject.Inject
import kotlin.coroutines.resume

class LocationService @Inject constructor(
    private val context: Context
) {
    private val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)

    fun hasLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(
                    context, Manifest.permission.ACCESS_COARSE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED
    }

    suspend fun getCurrentLocationName(): String? {
        if (!hasLocationPermission()) return null

        return try {
            val location = getLastLocation() ?: return null
            reverseGeocode(location.first, location.second)
        } catch (_: Exception) {
            null
        }
    }

    @Suppress("MissingPermission")
    private suspend fun getLastLocation(): Pair<Double, Double>? =
        suspendCancellableCoroutine { continuation ->
            val cancellationToken = CancellationTokenSource()

            fusedLocationClient.getCurrentLocation(
                Priority.PRIORITY_BALANCED_POWER_ACCURACY,
                cancellationToken.token
            ).addOnSuccessListener { location ->
                if (location != null) {
                    continuation.resume(location.latitude to location.longitude)
                } else {
                    fusedLocationClient.lastLocation.addOnSuccessListener { lastLoc ->
                        if (lastLoc != null) {
                            continuation.resume(lastLoc.latitude to lastLoc.longitude)
                        } else {
                            continuation.resume(null)
                        }
                    }.addOnFailureListener {
                        continuation.resume(null)
                    }
                }
            }.addOnFailureListener {
                continuation.resume(null)
            }

            continuation.invokeOnCancellation {
                cancellationToken.cancel()
            }
        }

    @Suppress("DEPRECATION")
    private fun reverseGeocode(lat: Double, lng: Double): String? {
        return try {
            val geocoder = Geocoder(context, Locale.getDefault())
            val addresses = geocoder.getFromLocation(lat, lng, 1)
            if (addresses.isNullOrEmpty()) return null

            val address = addresses[0]
            address.locality
                ?: address.subLocality
                ?: address.thoroughfare
                ?: address.subAdminArea
        } catch (_: Exception) {
            null
        }
    }
}
