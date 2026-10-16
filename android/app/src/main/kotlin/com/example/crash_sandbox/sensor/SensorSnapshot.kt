package com.example.crash_sandbox.sensor

data class SensorSnapshot(
    val timestamp: Long = System.currentTimeMillis(),
    val ax: Float = 0f,
    val ay: Float = 0f,
    val az: Float = 0f,
    val accMagnitude: Double = 0.0,
    val gx: Float = 0f,
    val gy: Float = 0f,
    val gz: Float = 0f,
    val gyroMagnitude: Double = 0.0,
    val worldHorizontalAcc: Double = 0.0,
    val worldVerticalAcc: Double = 0.0,
    val pressureHpa: Float = 0f
)