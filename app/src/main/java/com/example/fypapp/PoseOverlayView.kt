package com.example.fypapp

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import com.google.mlkit.vision.pose.Pose
import com.google.mlkit.vision.pose.PoseLandmark
import kotlin.math.atan2

class PoseOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var pose: Pose? = null
    private var imageWidth: Int = 0
    private var imageHeight: Int = 0

    private val paint = Paint().apply {
        color = Color.RED
        strokeWidth = 8f
        style = Paint.Style.FILL
    }

    var correctCount = 0
    private var isSquatting = false
    private var lastAngle = 0.0

    fun updatePose(pose: Pose, imageWidth: Int, imageHeight: Int) {
        this.pose = pose
        this.imageWidth = imageWidth
        this.imageHeight = imageHeight
        analyzeSquat(pose)
        invalidate()
    }

    private fun translateX(x: Float): Float {
        return x * width.toFloat() / imageWidth
    }

    private fun translateY(y: Float): Float {
        return y * height.toFloat() / imageHeight
    }

    private fun analyzeSquat(pose: Pose) {
        val leftHip = pose.getPoseLandmark(PoseLandmark.LEFT_HIP)
        val leftKnee = pose.getPoseLandmark(PoseLandmark.LEFT_KNEE)
        val leftAnkle = pose.getPoseLandmark(PoseLandmark.LEFT_ANKLE)

        val rightHip = pose.getPoseLandmark(PoseLandmark.RIGHT_HIP)
        val rightKnee = pose.getPoseLandmark(PoseLandmark.RIGHT_KNEE)
        val rightAnkle = pose.getPoseLandmark(PoseLandmark.RIGHT_ANKLE)

        if (leftHip != null && leftKnee != null && leftAnkle != null &&
            rightHip != null && rightKnee != null && rightAnkle != null) {

            val leftAngle = calculateAngle(leftHip, leftKnee, leftAnkle)
            val rightAngle = calculateAngle(rightHip, rightKnee, rightAnkle)

            val currentAngle = (leftAngle + rightAngle) / 2
            lastAngle = currentAngle

            val isCurrentlySquatting = currentAngle in 80.0..100.0

            if (isCurrentlySquatting && !isSquatting) {
                correctCount++
                isSquatting = true
            } else if (!isCurrentlySquatting && isSquatting) {
                isSquatting = false
            }
        }
    }

    private fun calculateAngle(
        first: PoseLandmark,
        middle: PoseLandmark,
        last: PoseLandmark
    ): Double {
        val angle = Math.toDegrees(
            atan2(
                (last.position.y - middle.position.y).toDouble(),
                (last.position.x - middle.position.x).toDouble()
            ) -
                    atan2(
                        (first.position.y - middle.position.y).toDouble(),
                        (first.position.x - middle.position.x).toDouble()
                    )
        )
        return Math.abs(angle)
    }

    fun getFeedbackMessage(): String {
        return when {
            isSquatting -> "Good squat position!"
            lastAngle > 100 -> "Bend your knees more"
            lastAngle < 80 -> "Don't bend too much"
            else -> "Stand straight and prepare for squat"
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        pose?.let { pose ->
            // Draw landmarks
            paint.style = Paint.Style.FILL
            for (landmark in pose.allPoseLandmarks) {
                canvas.drawCircle(
                    translateX(landmark.position.x),
                    translateY(landmark.position.y),
                    12f,
                    paint
                )
            }

            // Draw lines
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 6f

            drawLine(canvas, pose, PoseLandmark.LEFT_SHOULDER, PoseLandmark.RIGHT_SHOULDER)
            drawLine(canvas, pose, PoseLandmark.LEFT_SHOULDER, PoseLandmark.LEFT_ELBOW)
            drawLine(canvas, pose, PoseLandmark.LEFT_ELBOW, PoseLandmark.LEFT_WRIST)
            drawLine(canvas, pose, PoseLandmark.RIGHT_SHOULDER, PoseLandmark.RIGHT_ELBOW)
            drawLine(canvas, pose, PoseLandmark.RIGHT_ELBOW, PoseLandmark.RIGHT_WRIST)
            drawLine(canvas, pose, PoseLandmark.LEFT_SHOULDER, PoseLandmark.LEFT_HIP)
            drawLine(canvas, pose, PoseLandmark.RIGHT_SHOULDER, PoseLandmark.RIGHT_HIP)
            drawLine(canvas, pose, PoseLandmark.LEFT_HIP, PoseLandmark.RIGHT_HIP)
            drawLine(canvas, pose, PoseLandmark.LEFT_HIP, PoseLandmark.LEFT_KNEE)
            drawLine(canvas, pose, PoseLandmark.RIGHT_HIP, PoseLandmark.RIGHT_KNEE)
            drawLine(canvas, pose, PoseLandmark.LEFT_KNEE, PoseLandmark.LEFT_ANKLE)
            drawLine(canvas, pose, PoseLandmark.RIGHT_KNEE, PoseLandmark.RIGHT_ANKLE)
        }
    }

    private fun drawLine(canvas: Canvas, pose: Pose, startLandmarkType: Int, endLandmarkType: Int) {
        val startLandmark = pose.getPoseLandmark(startLandmarkType)
        val endLandmark = pose.getPoseLandmark(endLandmarkType)

        if (startLandmark != null && endLandmark != null) {
            canvas.drawLine(
                translateX(startLandmark.position.x),
                translateY(startLandmark.position.y),
                translateX(endLandmark.position.x),
                translateY(endLandmark.position.y),
                paint
            )
        }
    }
}