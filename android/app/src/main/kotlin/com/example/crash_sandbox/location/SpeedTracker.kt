package com.example.crash_sandbox.location

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle

interface SpeedChangeListener {
    fun onSpeedUpdated(speedKmh: Float, isVehicleMoving: Boolean)
}

class SpeedTracker(context: Context) : LocationListener {

    private val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager

    var currentSpeedKmh: Float = 0f
        private set

    var isVehicleSpeedConfirmed: Boolean = false
        private set

    private var previousSpeedKmh: Float = 0f
    private var speedChangeListener: SpeedChangeListener? = null

    fun setSpeedChangeListener(listener: SpeedChangeListener) {
        this.speedChangeListener = listener
    }

    @SuppressLint("MissingPermission")
    fun startTracking() {
        val isGpsEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
        if (isGpsEnabled) {
            // Low-power interval when idle: 2000ms with 2m distance constraint
            locationManager.requestLocationUpdates(
                LocationManager.GPS_PROVIDER,
                2000L,
                2f,
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
            currentSpeedKmh = location.speed * 3.6f
            
            val wasMoving = isVehicleSpeedConfirmed
            isVehicleSpeedConfirmed = currentSpeedKmh >= 25f

            // Trigger callback if driving state changed or speed updated
            speedChangeListener?.onSpeedUpdated(currentSpeedKmh, isVehicleSpeedConfirmed)
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