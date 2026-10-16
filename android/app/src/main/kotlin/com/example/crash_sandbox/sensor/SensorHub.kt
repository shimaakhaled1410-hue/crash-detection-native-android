package com.example.crash_sandbox.sensor

import android.content.Context
import com.example.crash_sandbox.math.CoordinateTransformer
import kotlin.math.sqrt

class SensorHub(context: Context, private val onSnapshotGenerated: (SensorSnapshot) -> Unit) {

    private val accelerometerSensor = AccelerometerSensor(context)
    private val gyroscopeSensor = GyroscopeSensor(context)
    private val rotationVectorSensor = RotationVectorSensor(context)
    private val barometerSensor = BarometerSensor(context)

    private val coordinateTransformer = CoordinateTransformer()

    private var lastGx = 0f
    private var lastGy = 0f
    private var lastGz = 0f
    private var lastGyroMag = 0.0

    private var currentPressureHpa = 0f
    private var isRunning = false

    fun start() {
        if (isRunning) return
        isRunning = true

        // 1. Rotation Vector
        if (rotationVectorSensor.doesSensorExist) {
            rotationVectorSensor.setOnSensorValuesChangedListener { values ->
                coordinateTransformer.updateRotationVector(values.toFloatArray())
            }
            rotationVectorSensor.startListening()
        }

        // 2. Barometer
        if (barometerSensor.doesSensorExist) {
            barometerSensor.setOnSensorValuesChangedListener { values ->
                currentPressureHpa = values[0]
            }
            barometerSensor.startListening()
        }

        // 3. Gyroscope
        if (gyroscopeSensor.doesSensorExist) {
            gyroscopeSensor.setOnSensorValuesChangedListener { values ->
                lastGx = values[0]
                lastGy = values[1]
                lastGz = values[2]
                lastGyroMag = sqrt((lastGx * lastGx + lastGy * lastGy + lastGz * lastGz).toDouble())
            }
            gyroscopeSensor.startListening()
        }

        // 4. Accelerometer & Frame Fusion
        if (accelerometerSensor.doesSensorExist) {
            accelerometerSensor.setOnSensorValuesChangedListener { values ->
                val currentTime = System.currentTimeMillis()
                val ax = values[0]
                val ay = values[1]
                val az = values[2]
                val accMagnitude = sqrt((ax * ax + ay * ay + az * az).toDouble())

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

                onSnapshotGenerated(snapshot)
            }
            accelerometerSensor.startListening()
        }
    }

    fun stop() {
        if (!isRunning) return
        isRunning = false

        accelerometerSensor.stopListening()
        gyroscopeSensor.stopListening()
        rotationVectorSensor.stopListening()
        barometerSensor.stopListening()
    }
}