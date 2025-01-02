package com.example.fypapp

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.util.Log
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
import com.google.mlkit.vision.pose.Pose
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

    private var correctCount = 0
    private var incorrectCount = 0
    private var currentState = "STANDING"
    private var isFullBodyVisible = false
    private var currentKneeAngle: Float = 0f
    private var heartRate = 0
    private var spO2 = 0
    private var isExercisePaused = false
    private var warningMessage = StringBuilder()

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

        setupCamera()
        setupVitalSignsMonitoring()
        updateCounters()
        updateState()
    }

    private fun setupVitalSignsMonitoring() {
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
    }

    private fun checkVitalSigns() {
        warningMessage.clear()
        var shouldPause = false

        when {
            heartRate < 60 -> {
                warningMessage.append("WARNING!\n\nHeart rate too low!\n($heartRate bpm)\n\nPlease take a rest!")
                shouldPause = true
            }
            heartRate > 100 -> {
                warningMessage.append("WARNING!\n\nHeart rate too high!\n($heartRate bpm)\n\nPlease take a rest!")
                shouldPause = true
            }
        }

        if (spO2 < 95) {
            if (warningMessage.isNotEmpty()) warningMessage.append("\n\n")
            warningMessage.append("WARNING!\n\nSpO2 too low!\n($spO2%)\n\nPlease take a rest!")
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
                .build()
                .also {
                    it.setSurfaceProvider(binding.viewFinder.surfaceProvider)
                }

            val imageAnalysis = ImageAnalysis.Builder()
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
                                processSquatState(pose)
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

            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    viewLifecycleOwner,
                    cameraSelector,
                    preview,
                    imageAnalysis
                )
            } catch (exc: Exception) {
                Log.e(TAG, "Use case binding failed", exc)
            }
        }, ContextCompat.getMainExecutor(requireContext()))
    }

    private fun processSquatState(pose: Pose) {
        isFullBodyVisible = binding.poseOverlayView.isFullBodyInFrame(pose)

        if (!isFullBodyVisible) {
            activity?.runOnUiThread {
                binding.feedbackTextView.text = "Please step back to show full body"
            }
            return
        }

        val kneeAngle = binding.poseOverlayView.calculateKneeAngle(pose)
        if (kneeAngle == null) {
            activity?.runOnUiThread {
                binding.feedbackTextView.text = "Cannot detect knee angle"
            }
            return
        }

        currentKneeAngle = kneeAngle

        val newState = when {
            kneeAngle < 110 -> "SQUATTING"
            else -> "STANDING"
        }

        if (currentState == "SQUATTING" && newState == "STANDING") {
            incrementCorrectCount()
        }

        currentState = newState

        activity?.runOnUiThread {
            updateAngleDisplay()
            binding.feedbackTextView.text = when (newState) {
                "SQUATTING" -> "Good! Now stand up"
                "STANDING" -> "Bend your knees more"
                else -> "Keep your form"
            }
            updateState()
        }
    }

    private fun updateVitalSignsDisplay() {
        binding.heartRateText.text = "Heart Rate: $heartRate bpm"
        binding.spO2Text.text = "SpO2: $spO2%"
    }

    private fun updateCounters() {
        binding.apply {
            correctCountText.text = "Correct: $correctCount"
            incorrectCountText.text = "Incorrect: $incorrectCount"
        }
    }

    private fun updateState() {
        binding.stateText.text = "State: $currentState"
    }

    private fun incrementCorrectCount() {
        if (!isExercisePaused) {
            correctCount++
            updateCounters()
        }
    }

    private fun incrementIncorrectCount() {
        if (!isExercisePaused) {
            incorrectCount++
            updateCounters()
        }
    }

    private fun updateAngleDisplay() {
        binding.angleText.text = String.format("Angle: %.1f°", currentKneeAngle)
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

    companion object {
        private const val TAG = "TestFragment"
        private const val REQUEST_CODE_PERMISSIONS = 10
        private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA)
    }
}