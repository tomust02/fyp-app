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

    companion object {
        private const val MIN_CONFIDENCE = 0.65f
        private const val REP_COOLDOWN_MS = 1500L

        // Squat angle thresholds
        private const val SQUAT_NORMAL_THRESHOLD = 32f
        private const val SQUAT_TRANSITION_MIN = 35f
        private const val SQUAT_TRANSITION_MAX = 65f
        private const val SQUAT_DOWN_MIN = 75f
        private const val SQUAT_DOWN_MAX = 95f

        // Push-up angle thresholds
        private const val PUSHUP_UP_THRESHOLD = 150f

        private const val PUSHUP_TRANSITION_MIN = 90f
        private const val PUSHUP_TRANSITION_MAX = 130f
        private const val PUSHUP_DOWN_MIN = 60f
        private const val PUSHUP_DOWN_MAX = 85f

        // Form check thresholds
        private const val HIP_FORWARD_THRESHOLD = 20f
        private const val HIP_BACKWARD_THRESHOLD = 45f
        private const val KNEE_OVER_TOE_THRESHOLD = 30f
        private const val MAX_ANGLE_CHANGE_PER_FRAME = 15f
        private const val PUSHUP_HIP_SAG_THRESHOLD = 15f
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

        if (shouldCountRep()) {
            repCounter++
            lastValidRepTime = System.currentTimeMillis()
            stateSequence.clear()
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

        if (shouldCountRep()) {
            repCounter++
            lastValidRepTime = System.currentTimeMillis()
            stateSequence.clear()
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
            angle in SQUAT_DOWN_MIN..SQUAT_DOWN_MAX -> PoseState.S3
            else -> PoseState.S2
        }
    }

    private fun determinePushupState(angle: Float): PoseState {
        return when {
            angle >= PUSHUP_UP_THRESHOLD -> PoseState.S1  // Up position
            angle in PUSHUP_TRANSITION_MIN..PUSHUP_TRANSITION_MAX -> PoseState.S2  // Transitioning
            angle in PUSHUP_DOWN_MIN..PUSHUP_DOWN_MAX -> PoseState.S3  // Down position
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
        val validSequence = when (currentExercise) {
            ExerciseType.SQUAT -> {
                stateSequence.size >= 3 &&
                        stateSequence[0] == PoseState.S2 &&
                        stateSequence[1] == PoseState.S3 &&
                        stateSequence[2] == PoseState.S2
            }
            ExerciseType.PUSHUP -> {
                stateSequence.size >= 3 &&
                        stateSequence[0] == PoseState.S1 &&  // Starting from up position
                        stateSequence[1] == PoseState.S3 &&  // Going to down position
                        stateSequence[2] == PoseState.S1     // Coming back to up position
            }
        }

        return validSequence &&
                System.currentTimeMillis() - lastValidRepTime > REP_COOLDOWN_MS
    }

    private fun checkSquatForm(hipAngle: Float, kneeAnkleAngle: Float, currentState: PoseState): Pair<Boolean, String> {
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

    private fun checkPushupForm(hipAngle: Float, elbowAngle: Float, currentState: PoseState): Pair<Boolean, String> {
        if (currentState == PoseState.S1) {
            return Pair(true, "Starting position")
        }

        return when {
            abs(hipAngle - 180f) > PUSHUP_HIP_SAG_THRESHOLD ->
                Pair(false, "Keep your body straight - don't sag your hips")
            elbowAngle < PUSHUP_DOWN_MIN ->
                Pair(false, "Don't go too low - protect your shoulders")
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
                PoseLandmark.LEFT_SHOULDER,
                PoseLandmark.RIGHT_SHOULDER,
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
                PoseLandmark.RIGHT_HIP,
                PoseLandmark.LEFT_KNEE,
                PoseLandmark.RIGHT_KNEE,
                PoseLandmark.LEFT_ANKLE,
                PoseLandmark.RIGHT_ANKLE
            )
        }

        return requiredLandmarks.all { landmarkType ->
            val landmark = pose.getPoseLandmark(landmarkType)
            landmark != null && landmark.inFrameLikelihood > MIN_CONFIDENCE
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
    }
}