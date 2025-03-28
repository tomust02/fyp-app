package com.example.fypapp

import android.content.Context
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader

class Scaler(context: Context, fileName: String) {
    private var min: Float = 0f
    private var scale: Float = 1f

    init {
        // Load the JSON file from assets
        val jsonString = context.assets.open(fileName).bufferedReader().use { it.readText() }
        val jsonObject = JSONObject(jsonString)
        min = jsonObject.getJSONArray("min").getDouble(0).toFloat()
        scale = jsonObject.getJSONArray("scale").getDouble(0).toFloat()
    }

    fun transform(value: Float): Float {
        // MinMaxScaler transform: (value - min) * scale
        return (value - min) * scale
    }

    fun inverseTransform(value: Float): Float {
        // MinMaxScaler inverse transform: value / scale + min
        return value / scale + min
    }
}