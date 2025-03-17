package com.example.fypapp

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.preference.PreferenceManager
import java.text.SimpleDateFormat
import java.util.*

class StepDetector(private val context: Context, private val sensorManager: SensorManager) : SensorEventListener {
    private var stepCount = 0

    init {
        resetStepCountIfNeeded()
    }

    fun start() {
        val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_NORMAL)
    }

    fun stop() {
        sensorManager.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent?) {
        event?.let {
            if (it.sensor.type == Sensor.TYPE_ACCELEROMETER) {
                val x = it.values[0]
                val y = it.values[1]
                val z = it.values[2]

                val magnitude = Math.sqrt((x * x + y * y + z * z).toDouble()).toFloat()
                if (magnitude > 12) { // Threshold value for step detection
                    stepCount++
                    saveStepCount()
                }
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    fun getStepCount(): Int {
        resetStepCountIfNeeded()
        return stepCount
    }

    private fun resetStepCountIfNeeded() {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val lastResetDate = prefs.getString("last_reset_date", null)
        val currentDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())

        if (lastResetDate == null || lastResetDate != currentDate) {
            stepCount = 0
            prefs.edit().putString("last_reset_date", currentDate).apply()
            saveStepCount()
        } else {
            stepCount = prefs.getInt("step_count", 0)
        }
    }

    private fun saveStepCount() {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        prefs.edit().putInt("step_count", stepCount).apply()
    }
}