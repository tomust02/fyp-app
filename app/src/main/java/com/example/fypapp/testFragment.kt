package com.example.fypapp

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.RectF
import android.os.Bundle
import android.util.Log
import android.util.Size
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.example.fypapp.databinding.FragmentTestBinding
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.ValueEventListener
import com.google.firebase.database.ktx.database
import com.google.firebase.ktx.Firebase
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.pose.PoseDetection
import com.google.mlkit.vision.pose.PoseDetector
import com.google.mlkit.vision.pose.accurate.AccuratePoseDetectorOptions
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class TestFragment : Fragment() {
    private var _binding: FragmentTestBinding? = null
    private val binding get() = _binding!!

    private lateinit var cameraExecutor: ExecutorService
    private lateinit var poseDetector: PoseDetector
    private lateinit var database: DatabaseReference
    private lateinit var poseClassifier: PoseClassifier

    private var heartRate = 0
    private var spO2 = 0
    private var breathingRate = 0  // Added breathing rate variable
    private var breathingStatus = "Unknown"  // Added breathing status variable
    private var breathingSensorValue = 0  // Added breathing sensor value variable

    private var isExercisePaused = false
    private var warningMessage = StringBuilder()

    private var currentExerciseType = ExerciseType.SQUAT

    private companion object {
        private const val TAG = "TestFragment"
        private const val REQUEST_CODE_PERMISSIONS = 10
        private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA)
        private const val STANDING_ANGLE_THRESHOLD = 165f
        private const val SQUAT_ANGLE_THRESHOLD = 120f
        private const val MIN_CONFIDENCE = 0.65f
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentTestBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        database = Firebase.database("https://fyp2024-abd3c-default-rtdb.firebaseio.com/").reference
        poseClassifier = PoseClassifier()

        setupCamera()
        setupVitalSignsMonitoring()
        setupButtons()
    }

    private fun setupButtons() {
        binding.resetButton.setOnClickListener {
            poseClassifier.reset()
            updateCounters(0, 0f)
        }

        binding.exerciseToggleButton.setOnClickListener {
            currentExerciseType = if (currentExerciseType == ExerciseType.SQUAT) {
                binding.exerciseToggleButton.text = "Switch to Squat"
                ExerciseType.PUSHUP
            } else {
                binding.exerciseToggleButton.text = "Switch to Push-up"
                ExerciseType.SQUAT
            }
            poseClassifier.setExerciseType(currentExerciseType)
        }
    }

    private fun setupCamera() {
        val options = AccuratePoseDetectorOptions.Builder()
            .setDetectorMode(AccuratePoseDetectorOptions.STREAM_MODE)
            .build()

        poseDetector = PoseDetection.getClient(options)
        cameraExecutor = Executors.newSingleThreadExecutor()

        if (allPermissionsGranted()) {
            startCamera()
        } else {
            requestPermissions(REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS)
        }
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(requireContext())

        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder()
                .setTargetResolution(Size(1080, 1920))  // Changed resolution to be more standard
                .build()
                .also {
                    it.setSurfaceProvider(binding.viewFinder.surfaceProvider)
                }

            val imageAnalysis = ImageAnalysis.Builder()
                .setTargetResolution(Size(1080, 1920))  // Changed resolution to be more standard
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()

            imageAnalysis.setAnalyzer(cameraExecutor) { imageProxy ->
                val mediaImage = imageProxy.image
                if (mediaImage != null) {
                    val image = InputImage.fromMediaImage(
                        mediaImage,
                        imageProxy.imageInfo.rotationDegrees
                    )

                    poseDetector.process(image)
                        .addOnSuccessListener { pose ->
                            binding.poseOverlayView.updatePose(pose)
                            if (!isExercisePaused) {
                                val screenRect = RectF(
                                    0f, 0f,
                                    binding.viewFinder.width.toFloat(),
                                    binding.viewFinder.height.toFloat()
                                )
                                val result = poseClassifier.processFrame(pose, screenRect)
                                updateUI(result)
                            }
                        }
                        .addOnFailureListener { e ->
                            Log.e(TAG, "Pose detection failed: ${e.message}")
                        }
                        .addOnCompleteListener {
                            imageProxy.close()
                        }
                } else {
                    imageProxy.close()
                }
            }

            // Try front camera first, fall back to back camera
            try {
                cameraProvider.unbindAll()

                // Try to bind with front camera
                try {
                    val frontCameraSelector = CameraSelector.Builder()
                        .requireLensFacing(CameraSelector.LENS_FACING_FRONT)
                        .build()

                    cameraProvider.bindToLifecycle(
                        viewLifecycleOwner,
                        frontCameraSelector,
                        preview,
                        imageAnalysis
                    )
                } catch (exc: Exception) {
                    // If front camera fails, try back camera
                    Log.e(TAG, "Front camera not available, trying back camera")
                    val backCameraSelector = CameraSelector.Builder()
                        .requireLensFacing(CameraSelector.LENS_FACING_BACK)
                        .build()

                    cameraProvider.bindToLifecycle(
                        viewLifecycleOwner,
                        backCameraSelector,
                        preview,
                        imageAnalysis
                    )
                }
            } catch (exc: Exception) {
                // If both cameras fail, try default camera
                Log.e(TAG, "Specific camera selection failed, trying default camera")
                try {
                    val defaultCameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
                    cameraProvider.bindToLifecycle(
                        viewLifecycleOwner,
                        defaultCameraSelector,
                        preview,
                        imageAnalysis
                    )
                } catch (exc: Exception) {
                    Log.e(TAG, "Use case binding failed", exc)
                    Toast.makeText(
                        requireContext(),
                        "Failed to start camera",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }, ContextCompat.getMainExecutor(requireContext()))
    }

    private fun updateUI(result: ClassificationResult) {
        activity?.runOnUiThread {
            binding.apply {
                // Convert the enum state names to display text
                val displayState = when(result.pose.uppercase()) {
                    "S1" -> "Standing State"
                    "S2" -> "Transition State"
                    "S3" -> "Squat State"
                    "UNKNOWN" -> "Unknown"
                    else -> result.pose.uppercase()
                }
                stateText.text = "State: $displayState"
                updateCounters(result.repCount, result.angle)
                feedbackTextView.text = result.feedback

                // Update visibility based on pose state
                when (result.pose) {
                    "unknown" -> {
                        warningOverlay.visibility = View.VISIBLE
                        warningMessageText.visibility = View.VISIBLE
                        warningMessageText.text = result.feedback
                        feedbackTextView.visibility = View.GONE
                    }
                    else -> {
                        if (warningMessage.isEmpty()) {
                            warningOverlay.visibility = View.GONE
                            warningMessageText.visibility = View.GONE
                            feedbackTextView.visibility = View.VISIBLE
                        }
                    }
                }
            }
        }
    }

    private fun updateCounters(repCount: Int, angle: Float) {
        activity?.runOnUiThread {
            binding.apply {
                correctCountText.text = "Correct: $repCount"
                angleText.text = String.format("Angle: %.1f°", angle)
            }
        }
    }

    private fun setupVitalSignsMonitoring() {
        // Listen for heart rate and SpO2 data
        database.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                heartRate = (snapshot.child("heart_rate").value as? Long)?.toInt() ?: 0
                spO2 = (snapshot.child("SPO2").value as? Long)?.toInt() ?: 0

                activity?.runOnUiThread {
                    updateVitalSignsDisplay()
                    checkVitalSigns()
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e(TAG, "Error: ${error.message}")
            }
        })

        // Listen for breathing data separately
        database.child("breathingData").addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                breathingRate = (snapshot.child("rate").value as? Long)?.toInt() ?: 0
                breathingStatus = snapshot.child("status").value as? String ?: "Unknown"
                breathingSensorValue = (snapshot.child("sensorValue").value as? Long)?.toInt() ?: 0

                activity?.runOnUiThread {
                    updateBreathingDisplay()
                    checkVitalSigns()
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e(TAG, "Error reading breathing data: ${error.message}")
            }
        })
    }

    private fun updateVitalSignsDisplay() {
        binding.heartRateText.text = "Heart Rate: $heartRate bpm"
        binding.spO2Text.text = "SpO2: $spO2%"
    }

    private fun updateBreathingDisplay() {
        // Define normal breathing rate range (typically 12-20 breaths per minute at rest)
        val minNormalBreathingRate = 12
        val maxNormalBreathingRate = 20

        // Determine breathing status display text
        val displayStatus = when {
            breathingRate == 0 -> "No breathing detected"
            breathingRate < minNormalBreathingRate || breathingRate > maxNormalBreathingRate -> "Abnormal"
            else -> "Normal"
        }

        // Update the text view with both the numerical rate and the status
        binding.breathingRateText.text = "Breathing: $breathingRate bpm\n$displayStatus"
    }

    private fun checkVitalSigns() {
        warningMessage.clear()
        var shouldPause = false

        when {
            heartRate < 0 -> {
                warningMessage.append("Heart rate too low ($heartRate bpm)\nPlease take a rest!")
                shouldPause = true
            }
            heartRate > 300 -> {
                warningMessage.append("Heart rate too high ($heartRate bpm)\nPlease take a rest!")
                shouldPause = true
            }
        }

        if (spO2 < 0) {
            if (warningMessage.isNotEmpty()) warningMessage.append("\n\n")
            warningMessage.append("SpO2 too low ($spO2%)\nPlease take a rest!")
            shouldPause = true
        }

        // Add breathing rate warning check if needed
        if (breathingStatus.contains("No breathing detected") && breathingRate == 0) {
            if (warningMessage.isNotEmpty()) warningMessage.append("\n\n")
            warningMessage.append("Warning: $breathingStatus\nPlease check your breathing sensor!")
            shouldPause = true
        }

        isExercisePaused = shouldPause

        binding.apply {
            if (warningMessage.isNotEmpty()) {
                warningOverlay.visibility = View.VISIBLE
                warningMessageText.visibility = View.VISIBLE
                warningMessageText.text = warningMessage.toString()
                feedbackTextView.visibility = View.GONE
            } else {
                warningOverlay.visibility = View.GONE
                warningMessageText.visibility = View.GONE
                feedbackTextView.visibility = View.VISIBLE
            }
        }
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(
            requireContext(), it) == PackageManager.PERMISSION_GRANTED
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {
                startCamera()
            } else {
                Toast.makeText(
                    requireContext(),
                    "Permissions not granted by the user.",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        cameraExecutor.shutdown()
        _binding = null
    }
}