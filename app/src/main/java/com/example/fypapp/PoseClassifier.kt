package com.example.fypapp

import android.graphics.RectF
import com.google.mlkit.vision.pose.Pose
import com.google.mlkit.vision.pose.PoseLandmark
import kotlin.math.abs

enum class ExerciseType {
    SQUAT,
    PUSHUP
}

data class ClassificationResult(
    val pose: String,
    val confidence: Float,
    val repCount: Int,
    val angle: Float,
    val feedback: String
)

class PoseClassifier {
    private var currentExercise = ExerciseType.SQUAT
    private var previousPose = PoseState.S1
    private var repCounter = 0
    private var lastValidRepTime = 0L
    private val poseSmoother = PoseSmoother()
    private val angleBuffer = ArrayDeque<Float>(5)
    private var lastValidAngle = 180f
    private val stateSequence = mutableListOf<PoseState>()

    // Track form status throughout the entire rep
    private var currentRepHasGoodForm = true

    companion object {
        private const val MIN_CONFIDENCE = 0.6f  // Slightly reduced confidence threshold
        private const val REP_COOLDOWN_MS = 1000L  // Reduced cooldown for more responsive counting

        // Squat angle thresholds - more forgiving
        private const val SQUAT_NORMAL_THRESHOLD = 40f  // Increased from 32f
        private const val SQUAT_TRANSITION_MIN = 41f    // Increased minimum
        private const val SQUAT_TRANSITION_MAX = 70f    // Increased maximum
        private const val SQUAT_DOWN_MIN = 71f          // Decreased minimum
        private const val SQUAT_DOWN_MAX = 120f         // Increased maximum range

        // Push-up angle thresholds - more forgiving
        private const val PUSHUP_UP_THRESHOLD = 140f    // Decreased from 150f
        private const val PUSHUP_TRANSITION_MIN = 85f   // Decreased minimum
        private const val PUSHUP_TRANSITION_MAX = 139f  // Increased maximum
        private const val PUSHUP_DOWN_MIN = 55f         // Decreased minimum
        private const val PUSHUP_DOWN_MAX = 90f         // Increased maximum range

        // Form check thresholds - much more forgiving
        private const val HIP_FORWARD_THRESHOLD = 10f         // Decreased from 20f
        private const val HIP_BACKWARD_THRESHOLD = 60f        // Increased from 45f
        private const val KNEE_OVER_TOE_THRESHOLD = 45f       // Increased from 30f
        private const val MAX_ANGLE_CHANGE_PER_FRAME = 20f    // Increased from 15f
        private const val PUSHUP_HIP_SAG_THRESHOLD = 25f      // Increased from 15f
    }

    fun setExerciseType(type: ExerciseType) {
        if (currentExercise != type) {
            currentExercise = type
            reset()
        }
    }

    fun processFrame(pose: Pose, screenRect: RectF): ClassificationResult {
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

        return when (currentExercise) {
            ExerciseType.SQUAT -> processSquat(smoothedPose)
            ExerciseType.PUSHUP -> processPushup(smoothedPose)
        }
    }

    private fun processSquat(pose: Pose): ClassificationResult {
        val hipKneeAngle = getVerticalAngle(
            pose.getPoseLandmark(PoseLandmark.LEFT_HIP),
            pose.getPoseLandmark(PoseLandmark.LEFT_KNEE)
        )
        val hipAngle = getHipAngle(pose)
        val kneeAnkleAngle = getVerticalAngle(
            pose.getPoseLandmark(PoseLandmark.LEFT_KNEE),
            pose.getPoseLandmark(PoseLandmark.LEFT_ANKLE)
        )

        val smoothedHipKneeAngle = smoothAngle(hipKneeAngle)
        val currentState = determineSquatState(smoothedHipKneeAngle)
        updateStateSequence(currentState)

        val (isProperForm, feedback) = checkSquatForm(hipAngle, kneeAnkleAngle, currentState)

        // Only mark as bad form for severe form issues
        if (!isProperForm && (hipAngle < 5f || hipAngle > 75f || kneeAnkleAngle > 60f)) {
            currentRepHasGoodForm = false
        }

        // Reset form tracking when starting a new rep
        if (currentState == PoseState.S1 && previousPose != PoseState.S1) {
            currentRepHasGoodForm = true
        }

        if (shouldCountRep()) {
            // More forgiving rep counting - count unless form was severely bad
            if (currentRepHasGoodForm) {
                repCounter++
                lastValidRepTime = System.currentTimeMillis()
                stateSequence.clear()
                return ClassificationResult(
                    currentState.name.lowercase(),
                    calculateConfidence(pose),
                    repCounter,
                    smoothedHipKneeAngle,
                    "Great job! Rep counted"
                )
            } else {
                // Only for severe form issues, don't count the rep
                lastValidRepTime = System.currentTimeMillis()
                stateSequence.clear()
                currentRepHasGoodForm = true  // Reset for next rep
                return ClassificationResult(
                    currentState.name.lowercase(),
                    calculateConfidence(pose),
                    repCounter,
                    smoothedHipKneeAngle,
                    "Rep not counted - try with better form"
                )
            }
        }

        previousPose = currentState
        lastValidAngle = smoothedHipKneeAngle

        return ClassificationResult(
            currentState.name.lowercase(),
            calculateConfidence(pose),
            repCounter,
            smoothedHipKneeAngle,
            feedback
        )
    }

    private fun processPushup(pose: Pose): ClassificationResult {
        val elbowAngle = getAngle(
            pose.getPoseLandmark(PoseLandmark.LEFT_SHOULDER),
            pose.getPoseLandmark(PoseLandmark.LEFT_ELBOW),
            pose.getPoseLandmark(PoseLandmark.LEFT_WRIST)
        )

        val hipAngle = getAngle(
            pose.getPoseLandmark(PoseLandmark.LEFT_SHOULDER),
            pose.getPoseLandmark(PoseLandmark.LEFT_HIP),
            pose.getPoseLandmark(PoseLandmark.LEFT_KNEE)
        )

        val smoothedElbowAngle = smoothAngle(elbowAngle)
        val currentState = determinePushupState(smoothedElbowAngle)
        updateStateSequence(currentState)

        val (isProperForm, feedback) = checkPushupForm(hipAngle, smoothedElbowAngle, currentState)

        // Only mark as bad form for severe form issues
        if (!isProperForm && (abs(hipAngle - 180f) > 35f || elbowAngle < 50f)) {
            currentRepHasGoodForm = false
        }

        // Reset form tracking when starting a new rep
        if (currentState == PoseState.S1 && previousPose != PoseState.S1) {
            currentRepHasGoodForm = true
        }

        if (shouldCountRep()) {
            // More forgiving rep counting - count unless form was severely bad
            if (currentRepHasGoodForm) {
                repCounter++
                lastValidRepTime = System.currentTimeMillis()
                stateSequence.clear()
                return ClassificationResult(
                    currentState.name.lowercase(),
                    calculateConfidence(pose),
                    repCounter,
                    smoothedElbowAngle,
                    "Great job! Rep counted"
                )
            } else {
                // Only for severe form issues, don't count the rep
                lastValidRepTime = System.currentTimeMillis()
                stateSequence.clear()
                currentRepHasGoodForm = true  // Reset for next rep
                return ClassificationResult(
                    currentState.name.lowercase(),
                    calculateConfidence(pose),
                    repCounter,
                    smoothedElbowAngle,
                    "Rep not counted - try with better form"
                )
            }
        }

        previousPose = currentState
        lastValidAngle = smoothedElbowAngle

        return ClassificationResult(
            currentState.name.lowercase(),
            calculateConfidence(pose),
            repCounter,
            smoothedElbowAngle,
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

    private fun determineSquatState(angle: Float): PoseState {
        return when {
            angle <= SQUAT_NORMAL_THRESHOLD -> PoseState.S1
            angle in SQUAT_TRANSITION_MIN..SQUAT_TRANSITION_MAX -> PoseState.S2
            angle >= SQUAT_DOWN_MIN -> PoseState.S3  // Any value above the threshold is considered squatting
            else -> PoseState.S2
        }
    }

    private fun determinePushupState(angle: Float): PoseState {
        return when {
            angle >= PUSHUP_UP_THRESHOLD -> PoseState.S1  // Up position
            angle in PUSHUP_TRANSITION_MIN..PUSHUP_TRANSITION_MAX -> PoseState.S2  // Transitioning
            angle <= PUSHUP_DOWN_MIN -> PoseState.S3  // Any value below the threshold is considered down
            else -> PoseState.S2
        }
    }

    private fun updateStateSequence(currentState: PoseState) {
        if (stateSequence.isEmpty() || stateSequence.last() != currentState) {
            stateSequence.add(currentState)
            if (stateSequence.size > 5) {  // Increased from 3 to keep more history
                stateSequence.removeAt(0)
            }
        }
    }

    private fun shouldCountRep(): Boolean {
        // First check if we have enough states recorded
        if (stateSequence.size < 3) {
            return false
        }

        // Create a condensed sequence by removing duplicate consecutive states
        val condensedSequence = mutableListOf<PoseState>()
        stateSequence.forEach { state ->
            if (condensedSequence.isEmpty() || condensedSequence.last() != state) {
                condensedSequence.add(state)
            }
        }

        // Look for the pattern in the condensed sequence
        var foundValidPattern = false
        if (condensedSequence.size >= 3) {
            for (i in 0..condensedSequence.size - 3) {
                if (condensedSequence[i] == PoseState.S1 &&
                    condensedSequence.subList(i, condensedSequence.size).contains(PoseState.S3) &&
                    condensedSequence.last() == PoseState.S1) {
                    foundValidPattern = true
                    break
                }
            }
        }

        return foundValidPattern && System.currentTimeMillis() - lastValidRepTime > REP_COOLDOWN_MS
    }

    private fun checkSquatForm(hipAngle: Float, kneeAnkleAngle: Float, currentState: PoseState): Pair<Boolean, String> {
        // Always consider the starting position as good form
        if (currentState == PoseState.S1) {
            return Pair(true, "Good starting position")
        }

        // Much more lenient thresholds
        val HIP_FORWARD_THRESHOLD_LENIENT = 10f    // Very lenient leaning forward
        val HIP_BACKWARD_THRESHOLD_LENIENT = 70f   // Very lenient leaning backward
        val KNEE_OVER_TOE_THRESHOLD_LENIENT = 50f  // Very lenient knee position

        // Only flag as incorrect form if significantly outside thresholds
        return when {
            hipAngle < HIP_FORWARD_THRESHOLD_LENIENT && hipAngle < 5f ->
                Pair(false, "Try to keep your back straighter")
            hipAngle > HIP_BACKWARD_THRESHOLD_LENIENT && hipAngle > 75f ->
                Pair(false, "Try not to lean too far back")
            kneeAnkleAngle > KNEE_OVER_TOE_THRESHOLD_LENIENT && kneeAnkleAngle > 60f ->
                Pair(false, "Watch your knees over toes")
            // Everything else is considered good form now
            hipAngle < HIP_FORWARD_THRESHOLD_LENIENT ->
                Pair(true, "Keep your chest up")
            hipAngle > HIP_BACKWARD_THRESHOLD_LENIENT ->
                Pair(true, "Lean forward slightly")
            kneeAnkleAngle > KNEE_OVER_TOE_THRESHOLD_LENIENT ->
                Pair(true, "Push through your heels")
            currentState == PoseState.S2 ->
                Pair(true, "Good movement")
            currentState == PoseState.S3 ->
                Pair(true, "Good depth")
            else ->
                Pair(true, "Good form")
        }
    }

    private fun checkPushupForm(hipAngle: Float, elbowAngle: Float, currentState: PoseState): Pair<Boolean, String> {
        // Always consider the starting position as good form
        if (currentState == PoseState.S1) {
            return Pair(true, "Good plank position")
        }

        // More lenient thresholds
        val PUSHUP_HIP_SAG_THRESHOLD_LENIENT = 25f   // Increased from 15f
        val PUSHUP_DOWN_MIN_LENIENT = 50f            // Decreased from 60f

        // Only flag as incorrect form if significantly outside thresholds
        return when {
            abs(hipAngle - 180f) > PUSHUP_HIP_SAG_THRESHOLD_LENIENT && abs(hipAngle - 180f) > 35f ->
                Pair(false, "Keep your body in a straight line")
            elbowAngle < PUSHUP_DOWN_MIN_LENIENT && elbowAngle < 45f ->
                Pair(false, "Don't go too low")
            // For minor form issues, give feedback but still count as good form
            abs(hipAngle - 180f) > PUSHUP_HIP_SAG_THRESHOLD_LENIENT ->
                Pair(true, "Tighten your core")
            elbowAngle < PUSHUP_DOWN_MIN_LENIENT ->
                Pair(true, "Good depth")
            currentState == PoseState.S2 ->
                Pair(true, "Good movement")
            currentState == PoseState.S3 ->
                Pair(true, "Good depth")
            else ->
                Pair(true, "Good form")
        }
    }

    private fun getVerticalAngle(top: PoseLandmark?, bottom: PoseLandmark?): Float {
        if (top == null || bottom == null) return 0f

        val deltaX = bottom.position.x - top.position.x
        val deltaY = bottom.position.y - top.position.y

        return abs((Math.toDegrees(kotlin.math.atan2(deltaX, deltaY).toDouble())).toFloat())
    }

    private fun getAngle(first: PoseLandmark?, middle: PoseLandmark?, last: PoseLandmark?): Float {
        if (first == null || middle == null || last == null) return 0f

        val angle = Math.toDegrees(
            kotlin.math.atan2(
                (last.position.y - middle.position.y).toDouble(),
                (last.position.x - middle.position.x).toDouble()
            ) -
                    kotlin.math.atan2(
                        (first.position.y - middle.position.y).toDouble(),
                        (first.position.x - middle.position.x).toDouble()
                    )
        ).toFloat()

        return abs(if (angle < 0) angle + 360f else angle)
    }

    private fun getHipAngle(pose: Pose): Float {
        val shoulder = pose.getPoseLandmark(PoseLandmark.LEFT_SHOULDER)
        val hip = pose.getPoseLandmark(PoseLandmark.LEFT_HIP)
        return getVerticalAngle(shoulder, hip)
    }

    private fun calculateConfidence(pose: Pose): Float {
        val landmarks = when (currentExercise) {
            ExerciseType.SQUAT -> listOf(
                PoseLandmark.LEFT_HIP,
                PoseLandmark.RIGHT_HIP,
                PoseLandmark.LEFT_KNEE,
                PoseLandmark.RIGHT_KNEE,
                PoseLandmark.LEFT_ANKLE,
                PoseLandmark.RIGHT_ANKLE
            )
            ExerciseType.PUSHUP -> listOf(
                PoseLandmark.LEFT_SHOULDER,
                PoseLandmark.RIGHT_SHOULDER,
                PoseLandmark.LEFT_ELBOW,
                PoseLandmark.RIGHT_ELBOW,
                PoseLandmark.LEFT_WRIST,
                PoseLandmark.RIGHT_WRIST,
                PoseLandmark.LEFT_HIP,
                PoseLandmark.RIGHT_HIP
            )
        }.mapNotNull { pose.getPoseLandmark(it) }

        val visibilityConfidence = landmarks.map { it.inFrameLikelihood }.average().toFloat()
        return if (visibilityConfidence > MIN_CONFIDENCE) visibilityConfidence else 0f
    }

    private fun areAllLandmarksVisible(pose: Pose): Boolean {
        val requiredLandmarks = when (currentExercise) {
            ExerciseType.SQUAT -> listOf(
                PoseLandmark.LEFT_HIP,
                PoseLandmark.RIGHT_HIP,
                PoseLandmark.LEFT_KNEE,
                PoseLandmark.RIGHT_KNEE,
                PoseLandmark.LEFT_ANKLE,
                PoseLandmark.RIGHT_ANKLE
            )
            ExerciseType.PUSHUP -> listOf(
                PoseLandmark.LEFT_SHOULDER,
                PoseLandmark.RIGHT_SHOULDER,
                PoseLandmark.LEFT_ELBOW,
                PoseLandmark.RIGHT_ELBOW,
                PoseLandmark.LEFT_WRIST,
                PoseLandmark.RIGHT_WRIST,
                PoseLandmark.LEFT_HIP,
                PoseLandmark.RIGHT_HIP
            )
        }

        // Reduced required confidence level for landmark detection
        return requiredLandmarks.all { landmarkType ->
            val landmark = pose.getPoseLandmark(landmarkType)
            landmark != null && landmark.inFrameLikelihood > MIN_CONFIDENCE * 0.8f
        }
    }

    fun reset() {
        repCounter = 0
        previousPose = PoseState.S1
        lastValidRepTime = 0L
        poseSmoother.reset()
        angleBuffer.clear()
        lastValidAngle = 180f
        stateSequence.clear()
        currentRepHasGoodForm = true
    }
}