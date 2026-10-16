package com.example.crash_sandbox.sensor

import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor

class AccelerometerSensor(context: Context) : AndroidSensor(
    context = context,
    sensorFeature = PackageManager.FEATURE_SENSOR_ACCELEROMETER,
    sensorType = Sensor.TYPE_ACCELEROMETER
)

class GyroscopeSensor(context: Context) : AndroidSensor(
    context = context,
    sensorFeature = PackageManager.FEATURE_SENSOR_GYROSCOPE,
    sensorType = Sensor.TYPE_GYROSCOPE
)

// Rotation vector sensor: tracks 3D device orientation in space using quaternions
class RotationVectorSensor(context: Context) : AndroidSensor(
    context = context,
    sensorFeature = PackageManager.FEATURE_SENSOR_COMPASS,
    sensorType = Sensor.TYPE_ROTATION_VECTOR
)

// Barometer sensor: captures cabin air pressure spikes during airbag deployment
class BarometerSensor(context: Context) : AndroidSensor(
    context = context,
    sensorFeature = PackageManager.FEATURE_SENSOR_BAROMETER,
    sensorType = Sensor.TYPE_PRESSURE
)