package com.example.util

import android.Manifest
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.Looper
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.example.data.JaranwalaRepository
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority

/**
 * Foreground Android Service that streams the Captain's real-time GPS coordinates to
 * [JaranwalaRepository.updateDriverLiveLocation] -> Firebase Firestore (/drivers & active /rides)
 * even when switching to maps/navigation, allowing passengers to watch live Captain progress.
 */
class DriverLocationTrackingService : Service() {

    private var fusedLocationClient: FusedLocationProviderClient? = null
    private var locationCallback: LocationCallback? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP_TRACKING || !hasLocationPermission(this)) {
            stopTrackingInternal()
            stopSelf()
            return START_NOT_STICKY
        }

        val activeRideId = intent?.getStringExtra(EXTRA_RIDE_ID)
        val notification = RideNotificationManager.buildLocationTrackingNotification(this, activeRideId)
        try {
            val fgType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
            } else {
                0
            }
            ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, fgType)
        } catch (_: SecurityException) {
            isRunning = false
            stopSelf()
            return START_NOT_STICKY
        } catch (_: Throwable) {
            isRunning = false
            stopSelf()
            return START_NOT_STICKY
        }

        isRunning = true
        lastTrackedRideId = activeRideId
        startLocationUpdates()
        return START_STICKY
    }

    private fun startLocationUpdates() {
        val repository = JaranwalaRepository.getInstance()
        try {
            repository.initRoomDatabase(applicationContext)
        } catch (_: Throwable) {}

        val client = fusedLocationClient ?: try {
            LocationServices.getFusedLocationProviderClient(this).also {
                fusedLocationClient = it
            }
        } catch (_: Throwable) {
            null
        } ?: return

        if (locationCallback != null) {
            return
        }

        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val loc = result.lastLocation ?: return
                if (PakistanGeoService.isWithinPakistan(loc.latitude, loc.longitude)) {
                    lastPushedCoordinates = loc.latitude to loc.longitude
                    repository.updateDriverLiveLocation(loc.latitude, loc.longitude)
                }
            }
        }
        locationCallback = callback

        try {
            val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 5000L)
                .setMinUpdateIntervalMillis(3000L)
                .setMinUpdateDistanceMeters(10f)
                .build()
            client.requestLocationUpdates(request, callback, Looper.getMainLooper())
            client.lastLocation.addOnSuccessListener { loc ->
                if (loc != null && PakistanGeoService.isWithinPakistan(loc.latitude, loc.longitude)) {
                    lastPushedCoordinates = loc.latitude to loc.longitude
                    repository.updateDriverLiveLocation(loc.latitude, loc.longitude)
                }
            }
        } catch (_: SecurityException) {
            stopTrackingInternal()
            stopSelf()
        } catch (_: Throwable) {}
    }

    private fun stopTrackingInternal() {
        locationCallback?.let { callback ->
            try {
                fusedLocationClient?.removeLocationUpdates(callback)
            } catch (_: Throwable) {}
        }
        locationCallback = null
        isRunning = false
        lastTrackedRideId = null
        try {
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        } catch (_: Throwable) {}
    }

    override fun onDestroy() {
        stopTrackingInternal()
        super.onDestroy()
    }

    companion object {
        const val ACTION_START_TRACKING = "com.example.action.START_LOCATION_TRACKING"
        const val ACTION_STOP_TRACKING = "com.example.action.STOP_LOCATION_TRACKING"
        const val EXTRA_RIDE_ID = "EXTRA_RIDE_ID"
        const val NOTIFICATION_ID = 8901

        @Volatile
        var isRunning: Boolean = false
            private set

        @Volatile
        var lastTrackedRideId: String? = null
            private set

        @Volatile
        var lastPushedCoordinates: Pair<Double, Double>? = null
            private set

        fun resetForTesting() {
            isRunning = false
            lastTrackedRideId = null
            lastPushedCoordinates = null
        }

        fun hasLocationPermission(context: Context): Boolean {
            return ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED
        }

        fun startTracking(context: Context, rideId: String? = null) {
            if (!hasLocationPermission(context)) return
            val intent = Intent(context, DriverLocationTrackingService::class.java).apply {
                action = ACTION_START_TRACKING
                if (!rideId.isNullOrBlank()) {
                    putExtra(EXTRA_RIDE_ID, rideId)
                }
            }
            try {
                ContextCompat.startForegroundService(context, intent)
            } catch (_: Throwable) {}
        }

        fun stopTracking(context: Context) {
            try {
                context.stopService(Intent(context, DriverLocationTrackingService::class.java))
            } catch (_: Throwable) {}
        }
    }
}
