package com.example.fypapp

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import androidx.fragment.app.Fragment
import com.example.fypapp.databinding.FragmentHeartHealthBinding
import com.google.firebase.database.*
import com.google.firebase.ml.modeldownloader.*
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

class HeartHealthFragment : Fragment() {
    private var _binding: FragmentHeartHealthBinding? = null
    private val binding get() = _binding!!
    private var tfliteInterpreter: Interpreter? = null
    private var maxHR: Float = 0.0f
    private val scalerMean = floatArrayOf(52.3f, 0.68f, 138.1f, 0.42f, 1.95f)
    private val scalerScale = floatArrayOf(9.1f, 0.47f, 24.8f, 0.49f, 1.15f)
    private lateinit var databaseRef: DatabaseReference
    private val TAG = "HeartHealthFragment"

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        Log.d(TAG, "onCreateView called")
        _binding = FragmentHeartHealthBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        Log.d(TAG, "onViewCreated called")

        databaseRef = FirebaseDatabase.getInstance().reference
        Log.d(TAG, "Database reference initialized")

        setupSpinners()
        loadModel()
        readdata()

        binding.predictButton.setOnClickListener {
            Log.d(TAG, "Predict button clicked")
            predictRisk()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
        Log.d(TAG, "onDestroyView called")
    }

    private fun setupSpinners() {
        try {
            binding.sexSpinner.adapter = ArrayAdapter.createFromResource(
                requireContext(), R.array.sex_options, android.R.layout.simple_spinner_item
            ).apply { setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }

            binding.exerciseAnginaSpinner.adapter = ArrayAdapter.createFromResource(
                requireContext(), R.array.yes_no_options, android.R.layout.simple_spinner_item
            ).apply { setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }

            binding.chestPainTypeSpinner.adapter = ArrayAdapter.createFromResource(
                requireContext(), R.array.chest_pain_options, android.R.layout.simple_spinner_item
            ).apply { setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
            Log.d(TAG, "Spinners initialized")
        } catch (e: Exception) {
            Log.e(TAG, "Spinner setup failed: ${e.message}", e)
        }
    }

    private fun loadModel() {
        val conditions = CustomModelDownloadConditions.Builder()
            .requireWifi()
            .build()
        FirebaseModelDownloader.getInstance()
            .getModel("HeartDiseasePredictor", DownloadType.LOCAL_MODEL_UPDATE_IN_BACKGROUND, conditions)
            .addOnSuccessListener { model ->
                try {
                    val file = model.file!!
                    val fileInputStream = FileInputStream(file)
                    val fileChannel = fileInputStream.channel
                    val buffer: MappedByteBuffer = fileChannel.map(FileChannel.MapMode.READ_ONLY, 0, file.length())
                    tfliteInterpreter = Interpreter(buffer)
                    binding.riskTextView.text = "Model loaded successfully"
                    Log.d(TAG, "Model loaded successfully")
                } catch (e: Exception) {
                    Log.e(TAG, "Model initialization failed: ${e.message}", e)
                    binding.riskTextView.text = "Model init failed: ${e.message}"
                }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Model download failed: ${e.message}", e)
                binding.riskTextView.text = "Model load failed: ${e.message}"
            }
    }

    private fun readdata() {
        databaseRef.child("MaxHR").addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val heartRate = snapshot.getValue(Int::class.java)
                heartRate?.let {
                    maxHR = it.toFloat()
                    binding.maxHRText.text = "MaxHR: $maxHR"
                    Log.d(TAG, "MaxHR updated: $maxHR")
                } ?: run {
                    binding.maxHRText.text = "MaxHR: N/A"
                    Log.w(TAG, "No heart_rate data in RTDB")
                }
            }

            override fun onCancelled(error: DatabaseError) {
                binding.maxHRText.text = "MaxHR Error: ${error.message}"
                Log.e(TAG, "RTDB read cancelled: ${error.message}")
            }
        })
    }

    private fun predictRisk() {
        if (tfliteInterpreter == null) {
            binding.riskTextView.text = "Error: Model not loaded"
            Log.e(TAG, "Predict failed: tfliteInterpreter is null")
            return
        }

        try {
            val input = FloatArray(5).apply {
                this[0] = binding.ageInput.text.toString().toFloat()
                this[1] = if (binding.sexSpinner.selectedItem.toString() == "Male") 1.0f else 0.0f
                this[2] = maxHR
                this[3] = if (binding.exerciseAnginaSpinner.selectedItem.toString() == "Yes") 1.0f else 0.0f
                val chestPainText = binding.chestPainTypeSpinner.selectedItem.toString()
                this[4] = when {
                    chestPainText.startsWith("Typical") -> 0.0f  // TA
                    chestPainText.startsWith("Atypical") -> 1.0f  // ATA
                    chestPainText.startsWith("Non-Anginal") -> 2.0f  // NAP
                    else -> 3.0f  // ASY
                }
                for (i in indices) {
                    this[i] = (this[i] - scalerMean[i]) / scalerScale[i]
                }
            }
            Log.d(TAG, "Input prepared: ${input.contentToString()}")

            val inputBuffer = Array(1) { input }
            val outputBuffer = Array(1) { FloatArray(1) }
            tfliteInterpreter!!.run(inputBuffer, outputBuffer)
            val predictionScore = outputBuffer[0][0]
            Log.d(TAG, "Prediction score: $predictionScore")
            val risk = if (predictionScore > 0.5) "High Risk" else "Low Risk"
            binding.riskTextView.text = "Heart Disease Risk: $risk"
        } catch (e: NumberFormatException) {
            binding.riskTextView.text = "Error: Invalid age input"
            Log.e(TAG, "Predict failed: Invalid age input - ${e.message}")
        } catch (e: Exception) {
            binding.riskTextView.text = "Error: Prediction failed - ${e.message}"
            Log.e(TAG, "Predict failed: ${e.message}", e)
        }
    }
}