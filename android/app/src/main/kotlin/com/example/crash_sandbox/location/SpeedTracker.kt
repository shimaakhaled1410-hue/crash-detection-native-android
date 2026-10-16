package com.example.crash_sandbox.location

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle

class SpeedTracker(private val context: Context) : LocationListener {

    private val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager

    var currentSpeedKmh: Float = 0f
        private set

    var isVehicleSpeedConfirmed: Boolean = false
        private set

    private var previousSpeedKmh: Float = 0f

    @SuppressLint("MissingPermission")
    fun startTracking() {
        val isGpsEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
        if (isGpsEnabled) {
            locationManager.requestLocationUpdates(
                LocationManager.GPS_PROVIDER,
                1000L,
                1f,
                this
            )
        }
    }

    fun stopTracking() {
        locationManager.removeUpdates(this)
    }

    override fun onLocationChanged(location: Location) {
        if (location.hasSpeed()) {
            previousSpeedKmh = currentSpeedKmh
            // location.speed is in m/s; multiply by 3.6 to convert to km/h
            currentSpeedKmh = location.speed * 3.6f
            isVehicleSpeedConfirmed = currentSpeedKmh >= 25f
        }
    }

    fun calculateSuddenSpeedDrop(): Float {
        val drop = previousSpeedKmh - currentSpeedKmh
        return if (drop > 0) drop else 0f
    }

    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
    override fun onProviderEnabled(provider: String) {}
    override fun onProviderDisabled(provider: String) {}
}