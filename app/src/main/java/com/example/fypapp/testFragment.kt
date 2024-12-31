package com.example.fypapp

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.example.fypapp.databinding.FragmentTestBinding
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.pose.PoseDetection
import com.google.mlkit.vision.pose.PoseDetector
import com.google.mlkit.vision.pose.accurate.AccuratePoseDetectorOptions
import com.google.mlkit.vision.pose.Pose
import com.google.mlkit.vision.pose.PoseLandmark
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class TestFragment : Fragment() {
    private var _binding: FragmentTestBinding? = null
    private val binding get() = _binding!!
    private lateinit var cameraExecutor: ExecutorService
    private lateinit var poseDetector: PoseDetector

    private var correctCount = 0
    private var incorrectCount = 0
    private var currentState = "STANDING"
    private var isFullBodyVisible = false
    private var currentKneeAngle: Float = 0f

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

        updateCounters()
        updateState()
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(requireContext())

        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder()
                .setTargetAspectRatio(AspectRatio.RATIO_16_9)
                .setTargetRotation(binding.viewFinder.display.rotation)
                .build()
                .also {
                    it.setSurfaceProvider(binding.viewFinder.surfaceProvider)
                }

            val imageAnalysis = ImageAnalysis.Builder()
                .setTargetAspectRatio(AspectRatio.RATIO_16_9)
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setOutputImageRotationEnabled(true)
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
                            if (pose.allPoseLandmarks.isNotEmpty()) {
                                binding.poseOverlayView.updatePose(pose)
                                processSquatState(pose)
                            } else {
                                activity?.runOnUiThread {
                                    binding.feedbackTextView.text = "No pose detected"
                                }
                            }
                        }
                        .addOnFailureListener { e ->
                            activity?.runOnUiThread {
                                binding.feedbackTextView.text = "Detection failed: ${e.message}"
                            }
                        }
                        .addOnCompleteListener {
                            imageProxy.close()
                        }
                } else {
                    imageProxy.close()
                }
            }

            val cameraSelector = CameraSelector.Builder()
                .requireLensFacing(CameraSelector.LENS_FACING_BACK)
                .build()

            try {
                cameraProvider.unbindAll()
                val camera = cameraProvider.bindToLifecycle(
                    viewLifecycleOwner,
                    cameraSelector,
                    preview,
                    imageAnalysis
                )
            } catch (exc: Exception) {
                exc.printStackTrace()
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

    private fun updateAngleDisplay() {
        binding.angleText.text = String.format("Angle: %.1f°", currentKneeAngle)
    }

    fun incrementCorrectCount() {
        correctCount++
        activity?.runOnUiThread {
            updateCounters()
        }
    }

    private fun updateCounters() {
        binding.correctCountText.text = "Correct: $correctCount"
        binding.incorrectCountText.text = "Incorrect: $incorrectCount"
    }

    private fun updateState() {
        binding.stateText.text = "State: $currentState"
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
        private const val REQUEST_CODE_PERMISSIONS = 10
        private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA)
    }
}