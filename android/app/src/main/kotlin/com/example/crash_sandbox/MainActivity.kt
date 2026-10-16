package com.example.crash_sandbox

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.app.ActivityCompat
import com.example.crash_sandbox.location.SpeedTracker
import com.example.crash_sandbox.math.CoordinateTransformer
import com.example.crash_sandbox.sensor.AccelerometerSensor
import com.example.crash_sandbox.sensor.BarometerSensor
import com.example.crash_sandbox.sensor.GyroscopeSensor
import com.example.crash_sandbox.sensor.RotationVectorSensor
import com.example.crash_sandbox.sensor.SensorSnapshot
import io.flutter.embedding.android.FlutterActivity
import kotlin.math.abs
import kotlin.math.sqrt

class MainActivity : FlutterActivity() {

    private val MY_TAG = "CrashSensorTest"
    private val LOCATION_PERMISSION_REQUEST_CODE = 1001

    // TESTING FLAG:
    // Set to true in production: triggers ONLY if moving vehicle speed >= 25 km/h
    // Set to false for indoor desk/hand testing (bypasses GPS speed check)
    private val ENFORCE_SPEED_CHECK = true

    private val PRE_IMPACT_WINDOW_SIZE = 60
    private val preImpactBuffer = ArrayDeque<SensorSnapshot>(PRE_IMPACT_WINDOW_SIZE)

    private val postImpactReadings = mutableListOf<SensorSnapshot>()
    private var isEvaluatingPostImpact = false
    private var isCooldownActive = false
    private var impactTimestamp: Long = 0

    // Coordinate frame transformer for Earth alignment
    private val coordinateTransformer = CoordinateTransformer()

    // Location & speed tracker
    private lateinit var speedTracker: SpeedTracker

    // Sensor instances
    private lateinit var accelerometerSensor: AccelerometerSensor
    private lateinit var gyroscopeSensor: GyroscopeSensor
    private lateinit var rotationVectorSensor: RotationVectorSensor
    private lateinit var barometerSensor: BarometerSensor

    // Last recorded values for fusion
    private var lastGx = 0f
    private var lastGy = 0f
    private var lastGz = 0f
    private var lastGyroMag = 0.0

    private var currentPressureHpa = 0f
    private var baselinePressureHpa = 0f

    private var lastLiveLogTime: Long = 0
    private val handler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        speedTracker = SpeedTracker(this)
        accelerometerSensor = AccelerometerSensor(this)
        gyroscopeSensor = GyroscopeSensor(this)
        rotationVectorSensor = RotationVectorSensor(this)
        barometerSensor = BarometerSensor(this)

        requestLocationPermissions()
        setupSensors()
    }

    private fun requestLocationPermissions() {
        val fineLocationGranted = ActivityCompat.checkSelfPermission(
            this, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (!fineLocationGranted) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
                LOCATION_PERMISSION_REQUEST_CODE
            )
        } else {
            speedTracker.startTracking()
            Log.i(MY_TAG, "Location speed tracking started.")
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE && grantResults.isNotEmpty() &&
            grantResults[0] == PackageManager.PERMISSION_GRANTED
        ) {
            speedTracker.startTracking()
            Log.i(MY_TAG, "Location permission granted. Tracking started.")
        }
    }

    private fun setupSensors() {
        // 1. Rotation Vector: maintains Earth-aligned rotation matrix
        if (rotationVectorSensor.doesSensorExist) {
            rotationVectorSensor.setOnSensorValuesChangedListener { values ->
                coordinateTransformer.updateRotationVector(values.toFloatArray())
            }
            rotationVectorSensor.startListening()
            Log.i(MY_TAG, "Rotation Vector tracking active.")
        }

        // 2. Barometer: tracks baseline and rapid air pressure changes
        if (barometerSensor.doesSensorExist) {
            barometerSensor.setOnSensorValuesChangedListener { values ->
                currentPressureHpa = values[0]
                if (baselinePressureHpa == 0f) {
                    baselinePressureHpa = currentPressureHpa
                }
            }
            barometerSensor.startListening()
            Log.i(MY_TAG, "Barometer tracking active.")
        }

        // 3. Gyroscope: tracks angular rotational velocity
        if (gyroscopeSensor.doesSensorExist) {
            gyroscopeSensor.setOnSensorValuesChangedListener { values ->
                lastGx = values[0]
                lastGy = values[1]
                lastGz = values[2]
                lastGyroMag = sqrt((lastGx * lastGx + lastGy * lastGy + lastGz * lastGz).toDouble())
            }
            gyroscopeSensor.startListening()
            Log.i(MY_TAG, "Gyroscope tracking active.")
        }

        // 4. Accelerometer: performs coordinate mapping, buffering, and trigger validation
        if (accelerometerSensor.doesSensorExist) {
            accelerometerSensor.setOnSensorValuesChangedListener { values ->
                val currentTime = System.currentTimeMillis()
                val ax = values[0]
                val ay = values[1]
                val az = values[2]
                val accMagnitude = sqrt((ax * ax + ay * ay + az * az).toDouble())

                // Transform raw sensor acceleration to Earth horizontal and vertical planes
                val worldAcc = coordinateTransformer.toWorldFrame(ax, ay, az)

                val snapshot = SensorSnapshot(
                    timestamp = currentTime,
                    ax = ax,
                    ay = ay,
                    az = az,
                    accMagnitude = accMagnitude,
                    gx = lastGx,
                    gy = lastGy,
                    gz = lastGz,
                    gyroMagnitude = lastGyroMag,
                    worldHorizontalAcc = worldAcc.horizontalMag,
                    worldVerticalAcc = worldAcc.verticalVal,
                    pressureHpa = currentPressureHpa
                )

                addToPreImpactBuffer(snapshot)

                if (isEvaluatingPostImpact) {
                    postImpactReadings.add(snapshot)

                    if (currentTime - lastLiveLogTime >= 500) {
                        val elapsed = currentTime - impactTimestamp
                        Log.d(
                            MY_TAG,
                            "⏱️ +${elapsed}ms | Acc: ${String.format("%.2f", accMagnitude)} | Horiz: ${String.format("%.2f", worldAcc.horizontalMag)} | Gyro: ${String.format("%.2f", lastGyroMag)}"
                        )
                        lastLiveLogTime = currentTime
                    }
                    return@setOnSensorValuesChangedListener
                }

                // SPEED GATE CHECK:
                // Verify vehicle speed condition before allowing impact detection
                val isSpeedConditionMet = !ENFORCE_SPEED_CHECK || speedTracker.isVehicleSpeedConfirmed

                // Primary trigger: horizontal planar spike + speed check + cooldown check
                if (!isCooldownActive && worldAcc.horizontalMag > 25.0) {
                    if (isSpeedConditionMet) {
                        startPostImpactEvaluation(worldAcc.horizontalMag, currentTime)
                    } else {
                        Log.d(
                            MY_TAG,
                            "⚠️ Impact rejected by Speed Gate. Current Speed: ${speedTracker.currentSpeedKmh} km/h (Requires >= 25 km/h)"
                        )
                    }
                }
            }
            accelerometerSensor.startListening()
            Log.i(MY_TAG, "Accelerometer tracking active.")
        }
    }

    private fun addToPreImpactBuffer(snapshot: SensorSnapshot) {
        if (preImpactBuffer.size >= PRE_IMPACT_WINDOW_SIZE) {
            preImpactBuffer.removeFirst()
        }
        preImpactBuffer.addLast(snapshot)
    }

    private fun startPostImpactEvaluation(horizontalImpact: Double, timestamp: Long) {
        isEvaluatingPostImpact = true
        impactTimestamp = timestamp
        lastLiveLogTime = timestamp
        postImpactReadings.clear()

        Log.w(
            MY_TAG,
            "🚨 IMPACT TRIGGERED (Horizontal: ${String.format("%.2f", horizontalImpact)} m/s²)! Speed: ${speedTracker.currentSpeedKmh} km/h. Monitoring stillness..."
        )

        handler.postDelayed({
            evaluateStillness()
        }, 4000)
    }

    private fun evaluateStillness() {
        isEvaluatingPostImpact = false
        isCooldownActive = true

        val settlingCutoff = impactTimestamp + 1800
        val stillnessSamples = postImpactReadings.filter { it.timestamp >= settlingCutoff }

        if (stillnessSamples.isEmpty()) {
            resetCooldown(5000)
            return
        }

        var totalGyro = 0.0
        var totalAccVariation = 0.0

        for (snapshot in stillnessSamples) {
            totalGyro += snapshot.gyroMagnitude
            totalAccVariation += abs(snapshot.accMagnitude - 9.8)
        }

        val avgGyro = totalGyro / stillnessSamples.size
        val avgAccVariation = totalAccVariation / stillnessSamples.size

        val pressureDelta = abs(currentPressureHpa - baselinePressureHpa)

        Log.i(
            MY_TAG,
            "🔍 Analysis: Avg Gyro = ${String.format("%.2f", avgGyro)} rad/s, Avg Acc Dev = ${String.format("%.2f", avgAccVariation)} m/s², ΔPressure = ${String.format("%.2f", pressureDelta)} hPa"
        )

        val isPhoneStill = avgGyro < 1.2 && avgAccVariation < 2.0

        if (isPhoneStill) {
            Log.e(MY_TAG, "🔴 CRASH CONFIRMED: Verified impact, coordinate alignment, and stillness. Triggering alert pipeline.")
        } else {
            Log.d(MY_TAG, "🟢 FALSE ALARM: Continuous movement detected post-impact. Dismissed.")
        }

        resetCooldown(7000)
    }

    private fun resetCooldown(delayMs: Long) {
        handler.postDelayed({
            isCooldownActive = false
            Log.i(MY_TAG, "✅ Cooldown expired. Trigger re-armed.")
        }, delayMs)
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
        speedTracker.stopTracking()
        accelerometerSensor.stopListening()
        gyroscopeSensor.stopListening()
        rotationVectorSensor.stopListening()
        barometerSensor.stopListening()
    }
}