package com.example.crash_sandbox

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import androidx.core.app.ActivityCompat
import com.example.crash_sandbox.detector.CrashDetectionListener
import com.example.crash_sandbox.detector.CrashDetector
import com.example.crash_sandbox.location.SpeedChangeListener
import com.example.crash_sandbox.location.SpeedTracker
import com.example.crash_sandbox.sensor.SensorHub
import com.example.crash_sandbox.sensor.SensorSnapshot
import io.flutter.embedding.android.FlutterActivity

class MainActivity : FlutterActivity(), SpeedChangeListener, CrashDetectionListener {

    private val TAG = "CrashSensorTest"
    private val LOCATION_PERMISSION_REQUEST = 1001

    // TESTING FLAG: Set to false to force sensors ON while testing indoors on your desk
    private val ENFORCE_SPEED_CHECK = false

    private lateinit var speedTracker: SpeedTracker
    private lateinit var sensorHub: SensorHub
    private lateinit var crashDetector: CrashDetector

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        crashDetector = CrashDetector(this)
        
        // SensorHub forwards generated snapshots straight to CrashDetector
        sensorHub = SensorHub(this) { snapshot ->
            crashDetector.processSnapshot(snapshot)
        }

        speedTracker = SpeedTracker(this)
        speedTracker.setSpeedChangeListener(this)

        requestLocationPermissions()

        // If testing on desk without car speed, activate sensors immediately
        if (!ENFORCE_SPEED_CHECK) {
            sensorHub.start()
            Log.i(TAG, "🧪 Desk Testing Mode: Sensors running continuously.")
        }
    }

    private fun requestLocationPermissions() {
        val granted = ActivityCompat.checkSelfPermission(
            this, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (!granted) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
                LOCATION_PERMISSION_REQUEST
            )
        } else {
            speedTracker.startTracking()
        }
    }

    // --- 1. Speed State Callbacks (Battery Optimization Gate) ---
    override fun onSpeedUpdated(speedKmh: Float, isVehicleMoving: Boolean) {
        if (!ENFORCE_SPEED_CHECK) return

        if (isVehicleMoving) {
            Log.i(TAG, "🚗 Vehicle moving at ${String.format("%.1f", speedKmh)} km/h. Sensors AWAKE.")
            sensorHub.start()
        } else if (speedKmh < 10f) {
            Log.i(TAG, "🛑 Vehicle idle/stopped (${String.format("%.1f", speedKmh)} km/h). Sensors SLEEPING.")
            sensorHub.stop()
        }
    }

    // --- 2. Crash Detection Pipeline Callbacks ---
    override fun onImpactTriggered(horizontalImpact: Double) {
        Log.w(TAG, "🚨 IMPACT TRIGGERED (Horizontal: ${String.format("%.2f", horizontalImpact)} m/s²)! Monitoring stillness...")
    }

    override fun onMonitoringTick(elapsedMs: Long, snapshot: SensorSnapshot) {
        Log.d(TAG, "⏱️ +${elapsedMs}ms | Acc: ${String.format("%.2f", snapshot.accMagnitude)} | Horiz: ${String.format("%.2f", snapshot.worldHorizontalAcc)} | Gyro: ${String.format("%.2f", snapshot.gyroMagnitude)}")
    }

    override fun onCrashConfirmed(avgGyro: Double, avgAccDeviation: Double, pressureDelta: Float) {
        Log.e(TAG, "🔴 CRASH CONFIRMED: High horizontal impact followed by stillness. Triggering alert pipeline.")
    }

    override fun onFalseAlarm(reason: String, avgGyro: Double, avgAccDeviation: Double) {
        Log.d(TAG, "🟢 FALSE ALARM: $reason (Avg Gyro: ${String.format("%.2f", avgGyro)} rad/s). Dismissed.")
    }

    override fun onDestroy() {
        super.onDestroy()
        speedTracker.stopTracking()
        sensorHub.stop()
        crashDetector.release()
    }
}