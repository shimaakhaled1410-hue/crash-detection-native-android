package com.example.crash_sandbox

import android.os.Bundle
import android.util.Log
import com.example.crash_sandbox.sensor.AccelerometerSensor
import io.flutter.embedding.android.FlutterActivity
import kotlin.math.sqrt

class MainActivity : FlutterActivity() {

    private val MY_TAG = "CrashSensorTest"
    private lateinit var accelerometerSensor: AccelerometerSensor

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        accelerometerSensor = AccelerometerSensor(this)

        if (accelerometerSensor.doesSensorExist) {
            Log.d(MY_TAG, "Accelerometer is available on this device!")

            accelerometerSensor.setOnSensorValuesChangedListener { values ->
                val x = values[0]
                val y = values[1]
                val z = values[2]

                val magnitude = sqrt((x * x + y * y + z * z).toDouble())

                if (magnitude > 25.0) {
                    Log.w(MY_TAG, "POTENTIAL IMPACT DETECTED! Magnitude: $magnitude m/s²")
                } else {
                    Log.d(MY_TAG, "Reading -> X: $x, Y: $y, Z: $z | Mag: $magnitude")
                }
            }

            accelerometerSensor.startListening()
        } else {
            Log.e(MY_TAG, "Accelerometer NOT found on this device!")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        accelerometerSensor.stopListening()
    }
}