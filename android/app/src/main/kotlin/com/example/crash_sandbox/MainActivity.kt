package com.example.crash_sandbox

import android.os.Bundle
import android.util.Log
import com.example.crash_sandbox.sensor.AccelerometerSensor
import com.example.crash_sandbox.sensor.GyroscopeSensor
import com.example.crash_sandbox.sensor.SensorSnapshot
import io.flutter.embedding.android.FlutterActivity
import kotlin.math.sqrt

class MainActivity : FlutterActivity() {

    private val MY_TAG = "CrashSensorTest"
    
    private val WINDOW_SIZE = 60
    private val sensorBuffer = ArrayDeque<SensorSnapshot>(WINDOW_SIZE)

    private var lastGx = 0f
    private var lastGy = 0f
    private var lastGz = 0f
    private var lastGyroMag = 0.0

    private var isBufferFullLogged = false

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

                addToBuffer(snapshot)

                if (accMagnitude > 20.0) {
                    onImpactDetected(accMagnitude)
                }
            }
            accelerometerSensor.startListening()
            Log.i(MY_TAG, "Accelerometer tracking started.")
        }
    }

    private fun addToBuffer(snapshot: SensorSnapshot) {
        if (sensorBuffer.size >= WINDOW_SIZE) {
            sensorBuffer.removeFirst()
            if (!isBufferFullLogged) {
                Log.i(MY_TAG, "BUFFER READY: 60 snapshots captured! Sliding window active.")
                isBufferFullLogged = true
            }
        }
        sensorBuffer.addLast(snapshot)
    }

    private fun onImpactDetected(impactMagnitude: Double) {
        Log.w(MY_TAG, "IMPACT TRIGGERED! Magnitude: $impactMagnitude m/s²")
        Log.i(MY_TAG, "AI Window Ready: ${sensorBuffer.size} snapshots available.")
        
        val oldest = sensorBuffer.firstOrNull()
        val latest = sensorBuffer.lastOrNull()
        if (oldest != null && latest != null) {
            val duration = latest.timestamp - oldest.timestamp
            Log.d(MY_TAG, "Pre-impact window duration: ${duration}ms")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        accelerometerSensor.stopListening()
        gyroscopeSensor.stopListening()
    }
}