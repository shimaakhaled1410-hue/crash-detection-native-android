package com.example.crash_sandbox

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.example.crash_sandbox.sensor.AccelerometerSensor
import com.example.crash_sandbox.sensor.GyroscopeSensor
import com.example.crash_sandbox.sensor.SensorSnapshot
import io.flutter.embedding.android.FlutterActivity
import kotlin.math.abs
import kotlin.math.sqrt

class MainActivity : FlutterActivity() {

    private val MY_TAG = "CrashSensorTest"

    private val PRE_IMPACT_WINDOW_SIZE = 60
    private val preImpactBuffer = ArrayDeque<SensorSnapshot>(PRE_IMPACT_WINDOW_SIZE)

    private val postImpactReadings = mutableListOf<SensorSnapshot>()
    private var isEvaluatingPostImpact = false
    private var isCooldownActive = false
    private var impactTimestamp: Long = 0

    private var lastGx = 0f
    private var lastGy = 0f
    private var lastGz = 0f
    private var lastGyroMag = 0.0

    private var lastLiveLogTime: Long = 0

    private val handler = Handler(Looper.getMainLooper())

    private lateinit var accelerometerSensor: AccelerometerSensor
    private lateinit var gyroscopeSensor: GyroscopeSensor

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        accelerometerSensor = AccelerometerSensor(this)
        gyroscopeSensor = GyroscopeSensor(this)

        setupSensors()
    }

    private fun setupSensors() {
        if (gyroscopeSensor.doesSensorExist) {
            gyroscopeSensor.setOnSensorValuesChangedListener { values ->
                lastGx = values[0]
                lastGy = values[1]
                lastGz = values[2]
                lastGyroMag = sqrt((lastGx * lastGx + lastGy * lastGy + lastGz * lastGz).toDouble())
            }
            gyroscopeSensor.startListening()
            Log.i(MY_TAG, "Gyroscope tracking started.")
        }

        if (accelerometerSensor.doesSensorExist) {
            accelerometerSensor.setOnSensorValuesChangedListener { values ->
                val currentTime = System.currentTimeMillis()
                val ax = values[0]
                val ay = values[1]
                val az = values[2]
                val accMagnitude = sqrt((ax * ax + ay * ay + az * az).toDouble())

                val snapshot = SensorSnapshot(
                    timestamp = currentTime,
                    ax = ax,
                    ay = ay,
                    az = az,
                    accMagnitude = accMagnitude,
                    gx = lastGx,
                    gy = lastGy,
                    gz = lastGz,
                    gyroMagnitude = lastGyroMag
                )

                addToPreImpactBuffer(snapshot)

                if (isEvaluatingPostImpact) {
                    postImpactReadings.add(snapshot)

                    if (currentTime - lastLiveLogTime >= 500) {
                        val elapsed = currentTime - impactTimestamp
                        Log.d(MY_TAG, "⏱️ Monitoring +${elapsed}ms -> Acc: ${String.format("%.2f", accMagnitude)} m/s² | Gyro: ${String.format("%.2f", lastGyroMag)} rad/s")
                        lastLiveLogTime = currentTime
                    }
                    return@setOnSensorValuesChangedListener
                }

                if (!isCooldownActive && accMagnitude > 25.0) {
                    startPostImpactEvaluation(accMagnitude, currentTime)
                }
            }
            accelerometerSensor.startListening()
            Log.i(MY_TAG, "Accelerometer tracking started.")
        }
    }

    private fun addToPreImpactBuffer(snapshot: SensorSnapshot) {
        if (preImpactBuffer.size >= PRE_IMPACT_WINDOW_SIZE) {
            preImpactBuffer.removeFirst()
        }
        preImpactBuffer.addLast(snapshot)
    }

    private fun startPostImpactEvaluation(impactMagnitude: Double, timestamp: Long) {
        isEvaluatingPostImpact = true
        impactTimestamp = timestamp
        lastLiveLogTime = timestamp
        postImpactReadings.clear()

        Log.w(MY_TAG, "🚨 IMPACT DETECTED (${String.format("%.2f", impactMagnitude)} m/s²)! Settling (1.8s) + Stillness Analysis (2.2s)...")

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

        Log.i(MY_TAG, "🔍 Analysis (Post-Settling): Samples = ${stillnessSamples.size}, Avg Acc Dev = ${String.format("%.2f", avgAccVariation)} m/s², Avg Gyro = ${String.format("%.2f", avgGyro)} rad/s")

        val isPhoneStill = avgGyro < 1.2 && avgAccVariation < 2.0

        if (isPhoneStill) {
            Log.e(MY_TAG, "🔴 CRASH CONFIRMED: High impact followed by verified stillness! Triggering emergency sequence.")
        } else {
            Log.d(MY_TAG, "🟢 FALSE ALARM: Continuous movement detected post-impact. Dismissed.")
        }

        resetCooldown(7000)
    }

    private fun resetCooldown(delayMs: Long) {
        Log.d(MY_TAG, "⏳ Cooldown active ($delayMs ms). Suppressing duplicate triggers.")
        handler.postDelayed({
            isCooldownActive = false
            Log.i(MY_TAG, "✅ Cooldown expired. Trigger re-armed.")
        }, delayMs)
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
        accelerometerSensor.stopListening()
        gyroscopeSensor.stopListening()
    }
}