package com.example.utils

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class LocationTracker private constructor(private val context: Context) {

    companion object {
        @SuppressLint("StaticFieldLeak")
        @Volatile
        private var instance: LocationTracker? = null

        fun getInstance(context: Context): LocationTracker {
            return instance ?: synchronized(this) {
                instance ?: LocationTracker(context.applicationContext).also { instance = it }
            }
        }
    }

    private val fusedLocationClient: FusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(context)
    private var lastLocation: Location? = null
    private val _currentLocation = MutableStateFlow<Location?>(null)
    val currentLocation: StateFlow<Location?> = _currentLocation
    private val _totalDistanceKm = MutableStateFlow(0.0)
    val totalDistanceKm: StateFlow<Double> = _totalDistanceKm
    
    private var trackingThread: android.os.HandlerThread? = null
    private var trackingLooper: android.os.Looper? = null

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(locationResult: LocationResult) {
            for (location in locationResult.locations) {
                _currentLocation.value = location
                // Filter out wildly inaccurate updates (relaxed from 50 to 100 for background highway drops)
                if (location.hasAccuracy() && location.accuracy > 100f) {
                    continue
                }

                val last = lastLocation
                if (last != null) {
                    val distanceInMeters = last.distanceTo(location)
                    
                    if (distanceInMeters < 2f) { // Reduced to 2 meters so we don't lose slow traffic distance
                        continue
                    }

                    val timeDiffSeconds = (location.time - last.time) / 1000.0
                    val calculatedSpeed = if (timeDiffSeconds > 0) distanceInMeters / timeDiffSeconds else 0.0

                    // Max speed 100 m/s (360 km/h). Anything faster is a GPS glitch.
                    if (calculatedSpeed <= 100.0) {
                        _totalDistanceKm.value += (distanceInMeters / 1000.0)
                        lastLocation = location
                    } else {
                        // If it's an anomaly, we don't add distance, but if a lot of time passed (e.g. > 30s),
                        // we should update lastLocation so we don't get stuck permanently.
                        if (timeDiffSeconds > 30.0) {
                            lastLocation = location
                        }
                    }
                } else {
                    lastLocation = location
                }
            }
        }
    }

    fun resetDistance(initialKm: Double = 0.0) {
        _totalDistanceKm.value = initialKm
        lastLocation = null
    }

    fun addSimulatedDistance(km: Double) {
        _totalDistanceKm.value += km
    }

    @SuppressLint("MissingPermission")
    fun startTracking() {
        val hasFine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val hasCoarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (!hasFine && !hasCoarse) {
            Log.w("LocationTracker", "Location permissions are not granted. Skipping tracking start.")
            return
        }
        
        if (trackingThread == null) {
            trackingThread = android.os.HandlerThread("LocationTrackingThread").apply { start() }
            trackingLooper = trackingThread?.looper
        }
        
        try {
            val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 3000L)
                .setMinUpdateDistanceMeters(0f) // Remove distance filter at request level to ensure we always get points
                .build()

            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                locationCallback,
                trackingLooper
            )
        } catch (e: Exception) {
            Log.e("LocationTracker", "Error starting location tracking: ${e.message}")
        }
    }

    fun stopTracking() {
        try {
            fusedLocationClient.removeLocationUpdates(locationCallback)
            lastLocation = null
        } catch (e: Exception) {
            Log.e("LocationTracker", "Error stopping location tracking: ${e.message}")
        }
    }
}
