package com.example.crash_sandbox

import android.os.Bundle
import android.util.Log
import com.example.crash_sandbox.sensor.AccelerometerSensor
import com.example.crash_sandbox.sensor.GyroscopeSensor
import io.flutter.embedding.android.FlutterActivity
import kotlin.math.sqrt

class MainActivity : FlutterActivity() {

    private val MY_TAG = "CrashSensorTest"
    
    private lateinit var accelerometerSensor: AccelerometerSensor
    private lateinit var gyroscopeSensor: GyroscopeSensor

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        accelerometerSensor = AccelerometerSensor(this)
        gyroscopeSensor = GyroscopeSensor(this)

        if (accelerometerSensor.doesSensorExist) {
            accelerometerSensor.setOnSensorValuesChangedListener { values ->
                val ax = values[0]
                val ay = values[1]
                val az = values[2]
                val accMagnitude = sqrt((ax * ax + ay * ay + az * az).toDouble())

                if (accMagnitude > 25.0) {
                    Log.w(MY_TAG, "ACCEL IMPACT! Magnitude: $accMagnitude m/s²")
                }
            }
            accelerometerSensor.startListening()
            Log.d(MY_TAG, "Accelerometer listening started.")
        } else {
            Log.e(MY_TAG, "Accelerometer not supported!")
        }

        if (gyroscopeSensor.doesSensorExist) {
            gyroscopeSensor.setOnSensorValuesChangedListener { values ->
                val gx = values[0]
                val gy = values[1]
                val gz = values[2]

                val gyroMagnitude = sqrt((gx * gx + gy * gy + gz * gz).toDouble())

                if (gyroMagnitude > 5.0) {
                    Log.w(MY_TAG, "HIGH ROTATION / ROLL! GyroMag: $gyroMagnitude rad/s")
                } else {
                    Log.d(MY_TAG, "Gyro -> X: $gx, Y: $gy, Z: $gz | Mag: $gyroMagnitude rad/s")
                }
            }
            gyroscopeSensor.startListening()
            Log.d(MY_TAG, "Gyroscope listening started.")
        } else {
            Log.e(MY_TAG, "Gyroscope not supported!")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        accelerometerSensor.stopListening()
        gyroscopeSensor.stopListening()
    }
}