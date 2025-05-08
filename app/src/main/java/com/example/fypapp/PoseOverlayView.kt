package com.example.fypapp

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PointF
import android.graphics.RectF
import android.util.AttributeSet
import android.util.Size
import android.view.View
import com.google.mlkit.vision.pose.Pose
import com.google.mlkit.vision.pose.PoseLandmark

class PoseOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var pose: Pose? = null
    private var imageWidth: Int = 0
    private var imageHeight: Int = 0

    // Default to 1080x1920 to match your camera setup==
    private var targetResolution = Size(1080, 1920)

    // Flag to control mirroring behavior
    private var disableMirroring = true

    // Adjustable parameters for skeleton alignment
    private var horizontalOffsetPercent = 0.00001f  // 15% of screen width to the right
    private var verticalOffsetPercent = 0.0008f    // 15% of screen height down
    private var skeletonScaleFactor = 1.5f      // 85% of original size

    private val pointPaint = Paint().apply {
        color = Color.RED
        strokeWidth = 10f
        style = Paint.Style.FILL
    }
    private val linePaint = Paint().apply {
        color = Color.GREEN
        strokeWidth = 5f
        style = Paint.Style.STROKE
    }

    fun updatePose(pose: Pose) {
        this.pose = pose
        invalidate()
    }

    fun setTargetResolution(width: Int, height: Int) {
        this.targetResolution = Size(width, height)
    }

    // Method to control mirroring behavior
    fun setMirroringDisabled(disabled: Boolean) {
        this.disableMirroring = disabled
        invalidate()
    }

    // Methods to adjust skeleton alignment
    fun setHorizontalOffset(percent: Float) {
        this.horizontalOffsetPercent = percent
        invalidate()
    }

    fun setVerticalOffset(percent: Float) {
        this.verticalOffsetPercent = percent
        invalidate()
    }

    fun setSkeletonScale(scale: Float) {
        this.skeletonScaleFactor = scale
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // Save current dimensions
        imageWidth = width
        imageHeight = height

        pose?.let { pose ->
            // Draw all points
            pose.allPoseLandmarks.forEach { landmark ->
                val point = translatePoint(landmark.position)
                canvas.drawCircle(
                    point.x,
                    point.y,
                    8f,
                    pointPaint
                )
            }

            // Draw lines connecting landmarks
            drawLine(canvas, pose, PoseLandmark.LEFT_SHOULDER, PoseLandmark.RIGHT_SHOULDER)
            drawLine(canvas, pose, PoseLandmark.LEFT_SHOULDER, PoseLandmark.LEFT_ELBOW)
            drawLine(canvas, pose, PoseLandmark.LEFT_ELBOW, PoseLandmark.LEFT_WRIST)
            drawLine(canvas, pose, PoseLandmark.RIGHT_SHOULDER, PoseLandmark.RIGHT_ELBOW)
            drawLine(canvas, pose, PoseLandmark.RIGHT_ELBOW, PoseLandmark.RIGHT_WRIST)
            drawLine(canvas, pose, PoseLandmark.LEFT_SHOULDER, PoseLandmark.LEFT_HIP)
            drawLine(canvas, pose, PoseLandmark.RIGHT_SHOULDER, PoseLandmark.RIGHT_HIP)
            drawLine(canvas, pose, PoseLandmark.LEFT_HIP, PoseLandmark.RIGHT_HIP)
            drawLine(canvas, pose, PoseLandmark.LEFT_HIP, PoseLandmark.LEFT_KNEE)
            drawLine(canvas, pose, PoseLandmark.LEFT_KNEE, PoseLandmark.LEFT_ANKLE)
            drawLine(canvas, pose, PoseLandmark.RIGHT_HIP, PoseLandmark.RIGHT_KNEE)
            drawLine(canvas, pose, PoseLandmark.RIGHT_KNEE, PoseLandmark.RIGHT_ANKLE)
        }
    }

    private fun translatePoint(point: PointF): PointF {
        // Apply mirroring if not disabled
        val adjustedPoint = if (disableMirroring) {
            // Use point directly without mirroring
            point
        } else {
            // Apply mirroring (for front camera)
            PointF(targetResolution.width - point.x, point.y)
        }

        // Scale the coordinates from the target resolution to the view size
        val scaleX = width.toFloat() / targetResolution.width.toFloat()
        val scaleY = height.toFloat() / targetResolution.height.toFloat()

        // Calculate the scaled coordinates with custom scale factor
        val scaledX = adjustedPoint.x * scaleX * skeletonScaleFactor
        val scaledY = adjustedPoint.y * scaleY * skeletonScaleFactor

        // Apply offsets for better alignment
        val horizontalOffset = width * horizontalOffsetPercent
        val verticalOffset = height * verticalOffsetPercent

        // Return the adjusted point
        return PointF(
            scaledX + horizontalOffset,
            scaledY + verticalOffset
        )
    }

    private fun drawLine(canvas: Canvas, pose: Pose, startLandmarkType: Int, endLandmarkType: Int) {
        val startLandmark = pose.getPoseLandmark(startLandmarkType)
        val endLandmark = pose.getPoseLandmark(endLandmarkType)
        if (startLandmark != null && endLandmark != null) {
            val startPoint = translatePoint(startLandmark.position)
            val endPoint = translatePoint(endLandmark.position)

            canvas.drawLine(
                startPoint.x,
                startPoint.y,
                endPoint.x,
                endPoint.y,
                linePaint
            )
        }
    }
}