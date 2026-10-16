package com.example.crash_sandbox.sensor

abstract class MeasurableSensor(protected val sensorType: Int) {
   
    abstract fun startListening()
    abstract fun stopListening()

    // protected means that this property can only be accessed
    // within this class and its subclasses
    
    protected fun onSensorValuesChanged:((List<Float>) -> Unit)? = null

    abstract val doesSensorExist: Boolean

    fun setOnSensorValuesChangedListener(listener: ((List<Float>) -> Unit)?) {
        onSensorValuesChanged = listener
    }
}