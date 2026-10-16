package com.example.crash_sandbox.detector

import com.example.crash_sandbox.sensor.SensorSnapshot

interface CrashDetectionListener {
    fun onImpactTriggered(horizontalImpact: Double)
    fun onMonitoringTick(elapsedMs: Long, snapshot: SensorSnapshot)
    fun onCrashConfirmed(avgGyro: Double, avgAccDeviation: Double, pressureDelta: Float)
    fun onFalseAlarm(reason: String, avgGyro: Double, avgAccDeviation: Double)
}