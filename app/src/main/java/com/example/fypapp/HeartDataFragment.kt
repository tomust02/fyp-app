package com.example.fypapp

import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.example.fypapp.databinding.FragmentHeartDataBinding
import com.google.firebase.database.*
import org.tensorflow.lite.Interpreter
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.LinkedList

class HeartDataFragment : Fragment() {
    private var _binding: FragmentHeartDataBinding? = null
    private val binding get() = _binding!!
    private lateinit var databaseRef: DatabaseReference
    private val handler = android.os.Handler(android.os.Looper.getMainLooper())
    private var lastHeartRate: Float? = null
    private var heartRateIndex: Int = 0 // Counter for raw heart rate data points
    private var smoothedHeartRateIndex: Int = 0 // Counter for smoothed heart rate data points

    // TensorFlow Lite model and scalers
    private lateinit var tflite: Interpreter
    private lateinit var scalerWindows: Scaler
    private lateinit var scalerLabels: Scaler
    private val windowSize = 10 // Must match the window_size used in Colab
    private val heartRateHistory = LinkedList<Float>() // Store the last 10 heart rate values for prediction
    private val smoothedHistory = LinkedList<Float>() // Store smoothed predictions for moving average

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHeartDataBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Initialize Firebase
        databaseRef = FirebaseDatabase.getInstance().getReference()

        // Initialize TensorFlow Lite model and scalers
        initializeTFLite()

        // Set different colors and y-axis ranges for the graphs
        binding.heartRateGraph.setLineColor(android.graphics.Color.RED)
        binding.heartRateGraph.setYRange(0f, 200f) // Raw heart rate range
        binding.spo2Graph.setLineColor(android.graphics.Color.BLUE)
        binding.spo2Graph.setYRange(0f, 200f) // Smoothed heart rate range (same as raw)

        // Fetch heart rate data
        fetchHeartRateData()

        // Start updating the graph every 1 second
        startGraphUpdateTimer()
    }

    private fun initializeTFLite() {
        try {
            // Load the TFLite model from assets
            val tfliteModel = loadModelFile(requireContext(), "heart_rate_model_combined.tflite")
            tflite = Interpreter(tfliteModel)

            // Load the scalers
            scalerWindows = Scaler(requireContext(), "scaler_windows.json")
            scalerLabels = Scaler(requireContext(), "scaler_labels.json")

            Log.d("HeartDataFragment", "TFLite model and scalers initialized successfully")
        } catch (e: Exception) {
            Log.e("HeartDataFragment", "Error initializing TFLite: ${e.message}")
        }
    }

    private fun loadModelFile(context: Context, fileName: String): ByteBuffer {
        val fileDescriptor = context.assets.openFd(fileName)
        val inputStream = fileDescriptor.createInputStream()
        val modelBuffer = inputStream.channel.map(
            java.nio.channels.FileChannel.MapMode.READ_ONLY,
            fileDescriptor.startOffset,
            fileDescriptor.declaredLength
        )
        inputStream.close()
        return modelBuffer
    }

    private fun fetchHeartRateData() {
        databaseRef.child("heart_rate").addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val heartRate = snapshot.getValue(Int::class.java)
                if (heartRate != null) {
                    lastHeartRate = heartRate.toFloat()
                    // Add the new heart rate value to the history
                    heartRateHistory.add(heartRate.toFloat())
                    // Keep only the last 10 values (window_size)
                    if (heartRateHistory.size > windowSize) {
                        heartRateHistory.removeFirst()
                    }
                    Log.d("HeartDataFragment", "Received heart rate from Firebase: $heartRate, History: $heartRateHistory")
                } else {
                    Log.d("HeartDataFragment", "No heart rate data available")
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("HeartDataFragment", "Failed to load heart rate data: ${error.message}")
                android.widget.Toast.makeText(context, "Failed to load heart rate data: ${error.message}", android.widget.Toast.LENGTH_LONG).show()
            }
        })
    }

    private fun getSmoothingWindow(): Int {
        if (smoothedHistory.size < 2) return 5
        val values = smoothedHistory.toFloatArray()
        val mean = values.average()
        val variance = values.map { (it - mean) * (it - mean) }.average()
        val stdDev = Math.sqrt(variance).toFloat()
        return when {
            stdDev > 30 -> 2 // High variability (intense exercise)
            stdDev > 15 -> 3 // Moderate variability (exercise)
            else -> 5 // Low variability (resting)
        }
    }

    private fun predictNextHeartRate(): Float {
        // Ensure we have enough data for a window
        if (heartRateHistory.size < windowSize || lastHeartRate == null) {
            return lastHeartRate ?: 60f // Default to 60 if not enough data
        }

        // Prepare the input window (last 10 values)
        val window = heartRateHistory.toFloatArray()
        val scaledWindow = FloatArray(windowSize) { i ->
            scalerWindows.transform(window[i])
        }

        // Reshape for TFLite model: [1, window_size, 1]
        val inputBuffer = ByteBuffer.allocateDirect(4 * windowSize * 1).apply {
            order(ByteOrder.nativeOrder())
            for (value in scaledWindow) {
                putFloat(value)
            }
            rewind()
        }

        // Prepare the output buffer: [1, 1]
        val outputBuffer = ByteBuffer.allocateDirect(4 * 1).apply {
            order(ByteOrder.nativeOrder())
        }

        // Run inference
        tflite.run(inputBuffer, outputBuffer)
        outputBuffer.rewind()
        val predictedScaled = outputBuffer.float

        // Inverse transform the prediction
        val predictedValue = scalerLabels.inverseTransform(predictedScaled)

        // Apply adaptive smoothing
        smoothedHistory.add(predictedValue)
        val smoothingWindow = getSmoothingWindow()
        if (smoothedHistory.size > smoothingWindow) {
            smoothedHistory.removeFirst()
        }
        val smoothedValue = smoothedHistory.average().toFloat()

        Log.d("HeartDataFragment", "Predicted heart rate (LSTM): $predictedValue, Smoothed: $smoothedValue, Smoothing Window: $smoothingWindow")
        return smoothedValue
    }

    private fun startGraphUpdateTimer() {
        val runnable = object : Runnable {
            override fun run() {
                // Update raw heart rate graph
                if (lastHeartRate != null) {
                    heartRateIndex++ // Increment the index for each update
                    Log.d("HeartDataFragment", "Adding raw heart rate: $lastHeartRate at index: $heartRateIndex")
                    binding.heartRateGraph.addDataPoint(heartRateIndex.toFloat(), lastHeartRate!!)
                } else {
                    Log.d("HeartDataFragment", "No heart rate data to plot yet")
                }

                // Update smoothed heart rate graph (replacing SpO2 graph)
                if (lastHeartRate != null) {
                    smoothedHeartRateIndex++ // Increment the index for each update
                    val predictedHeartRate = predictNextHeartRate()
                    Log.d("HeartDataFragment", "Adding smoothed heart rate: $predictedHeartRate at index: $smoothedHeartRateIndex")
                    binding.spo2Graph.addDataPoint(smoothedHeartRateIndex.toFloat(), predictedHeartRate)
                } else {
                    Log.d("HeartDataFragment", "No heart rate data to plot yet for smoothed graph")
                }

                // Redraw both graphs
                Log.d("HeartDataFragment", "Redrawing graphs")
                binding.heartRateGraph.invalidate()
                binding.spo2Graph.invalidate()

                // Repeat every 1 second
                handler.postDelayed(this, 1_000)
            }
        }
        handler.post(runnable)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        handler.removeCallbacksAndMessages(null)
        tflite.close()
        _binding = null
    }
}