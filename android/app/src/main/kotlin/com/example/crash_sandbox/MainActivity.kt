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

    private var lastGx = 0f
    private var lastGy = 0f
    private var lastGz = 0f
    private var lastGyroMag = 0.0

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
                val ax = values[0]
                val ay = values[1]
                val az = values[2]
                val accMagnitude = sqrt((ax * ax + ay * ay + az * az).toDouble())

                val snapshot = SensorSnapshot(
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
                    return@setOnSensorValuesChangedListener
                }

                if (!isCooldownActive && accMagnitude > 25.0) {
                    startPostImpactEvaluation(accMagnitude)
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

    private fun startPostImpactEvaluation(impactMagnitude: Double) {
        isEvaluatingPostImpact = true
        postImpactReadings.clear()

        Log.w(MY_TAG, "🚨 IMPACT DETECTED ($impactMagnitude m/s²)! Monitoring stillness for 3 seconds...")

        handler.postDelayed({
            evaluateStillness()
        }, 3000)
    }

    private fun evaluateStillness() {
        isEvaluatingPostImpact = false
        isCooldownActive = true

        if (postImpactReadings.isEmpty()) {
            resetCooldown(5000)
            return
        }

        var maxGyroInWindow = 0.0
        var totalAccVariation = 0.0

        for (snapshot in postImpactReadings) {
            if (snapshot.gyroMagnitude > maxGyroInWindow) {
                maxGyroInWindow = snapshot.gyroMagnitude
            }
            totalAccVariation += abs(snapshot.accMagnitude - 9.8)
        }

        val avgAccVariation = totalAccVariation / postImpactReadings.size

        Log.i(MY_TAG, "🔍 Analysis: Avg Acc Deviation = $avgAccVariation m/s², Max Gyro = $maxGyroInWindow rad/s")

        val isPhoneStill = maxGyroInWindow < 1.0 && avgAccVariation < 2.0

        if (isPhoneStill) {
            Log.e(MY_TAG, "🔴 CRASH CONFIRMED: High impact followed by stillness! Emergency sequence ready.")
        } else {
            Log.d(MY_TAG, "🟢 FALSE ALARM: Movement detected after impact. Dismissed.")
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