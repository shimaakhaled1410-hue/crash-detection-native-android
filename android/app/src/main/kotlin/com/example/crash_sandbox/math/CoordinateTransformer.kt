package com.example.crash_sandbox.math

import android.hardware.SensorManager
import kotlin.math.sqrt

data class WorldAcceleration(
    val horizontalMag: Double,
    val verticalVal: Double
)

class CoordinateTransformer {

    private val rotationMatrix = FloatArray(9)
    private var isRotationVectorAvailable = false

    fun updateRotationVector(rotationValues: FloatArray) {
        SensorManager.getRotationMatrixFromVector(rotationMatrix, rotationValues)
        isRotationVectorAvailable = true
    }

    fun toWorldFrame(ax: Float, ay: Float, az: Float): WorldAcceleration {
        if (!isRotationVectorAvailable) {
            val total = sqrt((ax * ax + ay * ay + az * az).toDouble())
            return WorldAcceleration(horizontalMag = total, verticalVal = 0.0)
        }

        // Multiply rotation matrix by device acceleration vector to map to Earth frame
        val wx = rotationMatrix[0] * ax + rotationMatrix[1] * ay + rotationMatrix[2] * az
        val wy = rotationMatrix[3] * ax + rotationMatrix[4] * ay + rotationMatrix[5] * az
        val wz = rotationMatrix[6] * ax + rotationMatrix[7] * ay + rotationMatrix[8] * az

        // Horizontal isolates forward/lateral crash impact; vertical represents speed bumps
        val horizontal = sqrt((wx * wx + wy * wy).toDouble())
        val vertical = wz.toDouble()

        return WorldAcceleration(horizontalMag = horizontal, verticalVal = vertical)
    }
}