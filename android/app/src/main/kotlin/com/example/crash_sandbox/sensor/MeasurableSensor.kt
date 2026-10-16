package com.example.crash_sandbox.sensor

abstract class MeasurableSensor(
    protected val sensorType: Int
) {
     // protected means that this property can only be accessed
    // within this class and its subclasses
   
    protected var onSensorValuesChanged: ((List<Float>) -> Unit)? = null

    abstract val doesSensorExist: Boolean

    abstract fun startListening()
    abstract fun stopListening()

    fun setOnSensorValuesChangedListener(listener: (List<Float>) -> Unit) {
        onSensorValuesChanged = listener
    }
}