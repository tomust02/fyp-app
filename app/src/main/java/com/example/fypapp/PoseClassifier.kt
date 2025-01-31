package com.example.fypapp

import android.graphics.PointF
import android.graphics.RectF
import com.google.mlkit.vision.pose.Pose
import com.google.mlkit.vision.pose.PoseLandmark
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

data class ClassificationResult(
    val pose: String,
    val confidence: Float,
    val repCount: Int,
    val angle: Float,
    val feedback: String
)

class PoseClassifier {
    private var previousPose = PoseState.S1
    private var repCounter = 0
    private var lastValidSquatTime = 0L
    private val poseSmoother = PoseSmoother()
    private val angleBuffer = ArrayDeque<Float>(5)
    private var lastValidAngle = 180f
    private val stateSequence = mutableListOf<PoseState>()

    companion object {
        private const val MIN_CONFIDENCE = 0.65f
        private const val SQUAT_COOLDOWN_MS = 1500L

        // Angle thresholds
        private const val NORMAL_ANGLE_THRESHOLD = 32f
        private const val TRANSITION_ANGLE_MIN = 35f
        private const val TRANSITION_ANGLE_MAX = 65f
        private const val SQUAT_ANGLE_MIN = 75f
        private const val SQUAT_ANGLE_MAX = 95f

        // Form check thresholds
        private const val HIP_FORWARD_THRESHOLD = 20f
        private const val HIP_BACKWARD_THRESHOLD = 45f
        private const val KNEE_OVER_TOE_THRESHOLD = 30f
        private const val MAX_ANGLE_CHANGE_PER_FRAME = 15f
    }

    fun processFrame(pose: Pose, screenRect: RectF): ClassificationResult {
        // Remove center check and only check if all landmarks are visible with good confidence
        val smoothedPose = poseSmoother.smoothPose(pose)
        if (!areAllLandmarksVisible(smoothedPose)) {
            return ClassificationResult(
                "unknown",
                0f,
                repCounter,
                lastValidAngle,
                "Please step back to show full body"
            )
        }

        val hipKneeAngle = getVerticalAngle(
            smoothedPose.getPoseLandmark(PoseLandmark.LEFT_HIP),
            smoothedPose.getPoseLandmark(PoseLandmark.LEFT_KNEE)
        )
        val hipAngle = getHipAngle(smoothedPose)
        val kneeAnkleAngle = getVerticalAngle(
            smoothedPose.getPoseLandmark(PoseLandmark.LEFT_KNEE),
            smoothedPose.getPoseLandmark(PoseLandmark.LEFT_ANKLE)
        )

        val smoothedHipKneeAngle = smoothAngle(hipKneeAngle)
        val currentState = determineState(smoothedHipKneeAngle)
        updateStateSequence(currentState)

        val (isProperForm, feedback) = checkForm(hipAngle, kneeAnkleAngle, currentState)

        if (shouldCountRep()) {
            repCounter++
            lastValidSquatTime = System.currentTimeMillis()
            stateSequence.clear()
        }

        previousPose = currentState
        lastValidAngle = smoothedHipKneeAngle

        return ClassificationResult(
            currentState.name.lowercase(),
            calculateConfidence(smoothedPose),
            repCounter,
            smoothedHipKneeAngle,
            feedback
        )
    }

    private fun smoothAngle(newAngle: Float): Float {
        while (angleBuffer.size >= 5) {
            angleBuffer.removeFirst()
        }

        if (angleBuffer.isNotEmpty()) {
            val lastAngle = angleBuffer.last()
            if (abs(newAngle - lastAngle) > MAX_ANGLE_CHANGE_PER_FRAME) {
                return lastAngle
            }
        }

        angleBuffer.addLast(newAngle)

        var smoothedAngle = 0f
        var totalWeight = 0f
        angleBuffer.forEachIndexed { index, angle ->
            val weight = (index + 1f)
            smoothedAngle += angle * weight
            totalWeight += weight
        }

        return smoothedAngle / totalWeight
    }





    private fun determineState(angle: Float): PoseState {
        return when {
            angle <= NORMAL_ANGLE_THRESHOLD -> PoseState.S1
            angle in TRANSITION_ANGLE_MIN..TRANSITION_ANGLE_MAX -> PoseState.S2
            angle in SQUAT_ANGLE_MIN..SQUAT_ANGLE_MAX -> PoseState.S3
            else -> PoseState.S2
        }
    }

    private fun updateStateSequence(currentState: PoseState) {
        if (stateSequence.isEmpty() || stateSequence.last() != currentState) {
            stateSequence.add(currentState)
            if (stateSequence.size > 3) {
                stateSequence.removeAt(0)
            }
        }
    }

    private fun shouldCountRep(): Boolean {
        return stateSequence.size >= 3 &&
                stateSequence[0] == PoseState.S2 &&
                stateSequence[1] == PoseState.S3 &&
                stateSequence[2] == PoseState.S2 &&
                previousPose == PoseState.S2 &&
                System.currentTimeMillis() - lastValidSquatTime > SQUAT_COOLDOWN_MS
    }

    private fun checkForm(hipAngle: Float, kneeAnkleAngle: Float, currentState: PoseState): Pair<Boolean, String> {
        if (currentState == PoseState.S1) {
            return Pair(true, "Good form!")
        }

        return when {
            hipAngle < HIP_FORWARD_THRESHOLD ->
                Pair(false, "Keep your back straight - don't lean forward")
            hipAngle > HIP_BACKWARD_THRESHOLD ->
                Pair(false, "Keep your back straight - don't lean backward")
            kneeAnkleAngle > KNEE_OVER_TOE_THRESHOLD ->
                Pair(false, "Keep your knees behind your toes")
            currentState == PoseState.S2 ->
                Pair(true, "Moving - maintain form")
            currentState == PoseState.S3 ->
                Pair(true, "Hold position")
            else ->
                Pair(true, "Good form!")
        }
    }

    private fun getVerticalAngle(top: PoseLandmark?, bottom: PoseLandmark?): Float {
        if (top == null || bottom == null) return 0f

        val deltaX = bottom.position.x - top.position.x
        val deltaY = bottom.position.y - top.position.y

        return abs((Math.toDegrees(kotlin.math.atan2(deltaX, deltaY).toDouble())).toFloat())
    }

    private fun getHipAngle(pose: Pose): Float {
        val shoulder = pose.getPoseLandmark(PoseLandmark.LEFT_SHOULDER)
        val hip = pose.getPoseLandmark(PoseLandmark.LEFT_HIP)
        return getVerticalAngle(shoulder, hip)
    }

    private fun calculateConfidence(pose: Pose): Float {
        val landmarks = listOf(
            PoseLandmark.LEFT_HIP,
            PoseLandmark.RIGHT_HIP,
            PoseLandmark.LEFT_KNEE,
            PoseLandmark.RIGHT_KNEE,
            PoseLandmark.LEFT_ANKLE,
            PoseLandmark.RIGHT_ANKLE
        ).mapNotNull { pose.getPoseLandmark(it) }

        val visibilityConfidence = landmarks.map { it.inFrameLikelihood }.average().toFloat()
        return if (visibilityConfidence > MIN_CONFIDENCE) visibilityConfidence else 0f
    }

    private fun areAllLandmarksVisible(pose: Pose): Boolean {
        val requiredLandmarks = listOf(
            PoseLandmark.LEFT_SHOULDER,
            PoseLandmark.RIGHT_SHOULDER,
            PoseLandmark.LEFT_HIP,
            PoseLandmark.RIGHT_HIP,
            PoseLandmark.LEFT_KNEE,
            PoseLandmark.RIGHT_KNEE,
            PoseLandmark.LEFT_ANKLE,
            PoseLandmark.RIGHT_ANKLE
        )

        // Check if all required landmarks are present with good confidence
        val allVisible = requiredLandmarks.all { landmarkType ->
            val landmark = pose.getPoseLandmark(landmarkType)
            landmark != null && landmark.inFrameLikelihood > MIN_CONFIDENCE
        }

        // Also check if landmarks are within the frame
        if (allVisible) {
            val landmarks = requiredLandmarks.mapNotNull { pose.getPoseLandmark(it) }
            val minY = landmarks.minOf { it.position.y }
            val maxY = landmarks.maxOf { it.position.y }

            // Ensure there's enough margin at top and bottom (5% of height)
            return true
        }

        return false
    }


    fun reset() {
        repCounter = 0
        previousPose = PoseState.S1
        lastValidSquatTime = 0L
        poseSmoother.reset()
        angleBuffer.clear()
        lastValidAngle = 180f
        stateSequence.clear()
    }
}