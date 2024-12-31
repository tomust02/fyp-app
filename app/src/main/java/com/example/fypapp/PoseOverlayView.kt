package com.example.fypapp

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import com.google.mlkit.vision.pose.Pose
import com.google.mlkit.vision.pose.PoseLandmark

class PoseOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var pose: Pose? = null

    private val paint = Paint().apply {
        color = Color.GREEN
        strokeWidth = 8f
        style = Paint.Style.STROKE
    }

    fun updatePose(pose: Pose) {
        this.pose = pose
        invalidate()
    }

    fun isFullBodyInFrame(pose: Pose): Boolean {
        val keyPoints = listOf(
            PoseLandmark.LEFT_SHOULDER,
            PoseLandmark.RIGHT_SHOULDER,
            PoseLandmark.LEFT_HIP,
            PoseLandmark.RIGHT_HIP,
            PoseLandmark.LEFT_KNEE,
            PoseLandmark.RIGHT_KNEE,
            PoseLandmark.LEFT_ANKLE,
            PoseLandmark.RIGHT_ANKLE
        )

        val visiblePoints = keyPoints.count { landmarkType ->
            pose.getPoseLandmark(landmarkType)?.inFrameLikelihood ?: 0f > 0.5f
        }

        // Consider it valid if at least 75% of key points are visible
        return visiblePoints >= (keyPoints.size * 0.75)
    }

    fun calculateKneeAngle(pose: Pose): Float? {
        val hip = pose.getPoseLandmark(PoseLandmark.LEFT_HIP)
        val knee = pose.getPoseLandmark(PoseLandmark.LEFT_KNEE)
        val ankle = pose.getPoseLandmark(PoseLandmark.LEFT_ANKLE)

        if (hip == null || knee == null || ankle == null) return null

        // Calculate vectors
        val upperLegX = hip.position.x - knee.position.x
        val upperLegY = hip.position.y - knee.position.y
        val lowerLegX = ankle.position.x - knee.position.x
        val lowerLegY = ankle.position.y - knee.position.y

        // Calculate angle using dot product
        val dotProduct = upperLegX * lowerLegX + upperLegY * lowerLegY
        val upperLegLength = kotlin.math.sqrt(upperLegX * upperLegX + upperLegY * upperLegY)
        val lowerLegLength = kotlin.math.sqrt(lowerLegX * lowerLegX + lowerLegY * lowerLegY)

        return kotlin.math.acos(dotProduct / (upperLegLength * lowerLegLength)) * 180f / Math.PI.toFloat()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val pose = pose ?: return

        // Draw landmarks
        pose.allPoseLandmarks.forEach { landmark ->
            canvas.drawCircle(
                landmark.position.x,
                landmark.position.y,
                8f,
                paint
            )
        }

        // Draw connections
        drawConnection(canvas, pose, PoseLandmark.LEFT_SHOULDER, PoseLandmark.RIGHT_SHOULDER)
        drawConnection(canvas, pose, PoseLandmark.LEFT_SHOULDER, PoseLandmark.LEFT_ELBOW)
        drawConnection(canvas, pose, PoseLandmark.RIGHT_SHOULDER, PoseLandmark.RIGHT_ELBOW)
        drawConnection(canvas, pose, PoseLandmark.LEFT_ELBOW, PoseLandmark.LEFT_WRIST)
        drawConnection(canvas, pose, PoseLandmark.RIGHT_ELBOW, PoseLandmark.RIGHT_WRIST)
        drawConnection(canvas, pose, PoseLandmark.LEFT_SHOULDER, PoseLandmark.LEFT_HIP)
        drawConnection(canvas, pose, PoseLandmark.RIGHT_SHOULDER, PoseLandmark.RIGHT_HIP)
        drawConnection(canvas, pose, PoseLandmark.LEFT_HIP, PoseLandmark.RIGHT_HIP)
        drawConnection(canvas, pose, PoseLandmark.LEFT_HIP, PoseLandmark.LEFT_KNEE)
        drawConnection(canvas, pose, PoseLandmark.RIGHT_HIP, PoseLandmark.RIGHT_KNEE)
        drawConnection(canvas, pose, PoseLandmark.LEFT_KNEE, PoseLandmark.LEFT_ANKLE)
        drawConnection(canvas, pose, PoseLandmark.RIGHT_KNEE, PoseLandmark.RIGHT_ANKLE)
    }

    private fun drawConnection(canvas: Canvas, pose: Pose, startLandmarkType: Int, endLandmarkType: Int) {
        val startLandmark = pose.getPoseLandmark(startLandmarkType)
        val endLandmark = pose.getPoseLandmark(endLandmarkType)

        if (startLandmark != null && endLandmark != null) {
            canvas.drawLine(
                startLandmark.position.x,
                startLandmark.position.y,
                endLandmark.position.x,
                endLandmark.position.y,
                paint
            )
        }
    }
}