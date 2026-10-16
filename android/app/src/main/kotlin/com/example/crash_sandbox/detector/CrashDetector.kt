package com.example.crash_sandbox.detector

import android.os.Handler
import android.os.Looper
import com.example.crash_sandbox.sensor.SensorSnapshot
import kotlin.math.abs

class CrashDetector(
    private val listener: CrashDetectionListener
) {
    private val PRE_IMPACT_WINDOW_SIZE = 60
    private val preImpactBuffer = ArrayDeque<SensorSnapshot>(PRE_IMPACT_WINDOW_SIZE)

    private val postImpactReadings = mutableListOf<SensorSnapshot>()
    private var isEvaluatingPostImpact = false
    private var isCooldownActive = false
    private var impactTimestamp: Long = 0

    private var baselinePressureHpa = 0f
    private var lastLiveLogTime: Long = 0

    private val handler = Handler(Looper.getMainLooper())

    fun processSnapshot(snapshot: SensorSnapshot) {
        if (baselinePressureHpa == 0f && snapshot.pressureHpa > 0f) {
            baselinePressureHpa = snapshot.pressureHpa
        }

        addToPreImpactBuffer(snapshot)

        val currentTime = snapshot.timestamp

        // 1. Post-impact monitoring stage
        if (isEvaluatingPostImpact) {
            postImpactReadings.add(snapshot)

            if (currentTime - lastLiveLogTime >= 500) {
                val elapsed = currentTime - impactTimestamp
                listener.onMonitoringTick(elapsed, snapshot)
                lastLiveLogTime = currentTime
            }
            return
        }

        // 2. Impact trigger (Earth-aligned horizontal acceleration > 25 m/s²)
        if (!isCooldownActive && snapshot.worldHorizontalAcc > 25.0) {
            startPostImpactEvaluation(snapshot.worldHorizontalAcc, currentTime)
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

        listener.onImpactTriggered(horizontalImpact)

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
        var latestPressure = baselinePressureHpa

        for (snapshot in stillnessSamples) {
            totalGyro += snapshot.gyroMagnitude
            totalAccVariation += abs(snapshot.accMagnitude - 9.8)
            latestPressure = snapshot.pressureHpa
        }

        val avgGyro = totalGyro / stillnessSamples.size
        val avgAccVariation = totalAccVariation / stillnessSamples.size
        val pressureDelta = abs(latestPressure - baselinePressureHpa)

        val isPhoneStill = avgGyro < 1.2 && avgAccVariation < 2.0

        if (isPhoneStill) {
            listener.onCrashConfirmed(avgGyro, avgAccVariation, pressureDelta)
        } else {
            listener.onFalseAlarm("Continuous motion detected after impact", avgGyro, avgAccVariation)
        }

        resetCooldown(7000)
    }

    private fun resetCooldown(delayMs: Long) {
        handler.postDelayed({
            isCooldownActive = false
        }, delayMs)
    }

    fun release() {
        handler.removeCallbacksAndMessages(null)
    }
}