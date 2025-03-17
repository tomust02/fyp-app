package com.example.fypapp

import com.google.mlkit.vision.pose.Pose
import com.google.mlkit.vision.pose.PoseLandmark
import android.graphics.PointF

class PoseSmoother {
    private val bufferSize = 20 // Increased buffer size for better detection
    private val landmarkBuffers = mutableMapOf<Int, MutableList<LandmarkPosition>>()
    private val smoothedPositions = mutableMapOf<Int, PointF>()
    private val movementHistory = mutableListOf<Float>()
    private val maxMovementHistory = 15

    data class LandmarkPosition(
        val x: Float,
        val y: Float,
        val visibility: Float,
        val timestamp: Long
    )

    fun smoothPose(pose: Pose): Pose {
        val currentTime = System.currentTimeMillis()

        // Update buffers with new landmarks
        pose.allPoseLandmarks.forEach { landmark ->
            val landmarkId = landmark.landmarkType
            if (!landmarkBuffers.containsKey(landmarkId)) {
                landmarkBuffers[landmarkId] = mutableListOf()
            }

            val buffer = landmarkBuffers[landmarkId]!!
            buffer.add(LandmarkPosition(
                landmark.position.x,
                landmark.position.y,
                landmark.inFrameLikelihood,
                currentTime
            ))

            // Keep buffer size limited
            while (buffer.size > bufferSize) {
                buffer.removeAt(0)
            }

            // Calculate smoothed position
            if (buffer.size >= 3) {
                val smoothedPosition = getSmoothedPosition(buffer)
                smoothedPositions[landmarkId] = PointF(smoothedPosition.x, smoothedPosition.y)
            } else {
                smoothedPositions[landmarkId] = landmark.position
            }
        }

        // Calculate and record overall movement
        if (landmarkBuffers.isNotEmpty() && landmarkBuffers.values.first().size >= 2) {
            recordMovement()
        }

        return pose // Return original pose since we'll use smoothedPositions separately
    }

    fun getSmoothPosition(landmarkType: Int): PointF? {
        return smoothedPositions[landmarkType]
    }

    private fun recordMovement() {
        var totalMovement = 0f
        var countedLandmarks = 0

        // Key landmarks that should move naturally in a real person
        val keyLandmarks = listOf(
            PoseLandmark.NOSE,
            PoseLandmark.LEFT_SHOULDER,
            PoseLandmark.RIGHT_SHOULDER
        )

        for (landmarkId in keyLandmarks) {
            val buffer = landmarkBuffers[landmarkId] ?: continue
            if (buffer.size < 2) continue

            val newest = buffer.last()
            val previous = buffer[buffer.size - 2]

            val dx = newest.x - previous.x
            val dy = newest.y - previous.y
            val distance = kotlin.math.sqrt(dx * dx + dy * dy)

            totalMovement += distance
            countedLandmarks++
        }

        if (countedLandmarks > 0) {
            val avgMovement = totalMovement / countedLandmarks
            movementHistory.add(avgMovement)

            while (movementHistory.size > maxMovementHistory) {
                movementHistory.removeAt(0)
            }
        }
    }

    // RELAXED criteria for natural movement
    fun hasNaturalMovement(): Boolean {
        // Always return true until we have enough history
        if (movementHistory.size < 10) return true

        // Calculate statistics about movement
        val avgMovement = movementHistory.average()

        // Real people can be still too, so only check for excessive movement
        val notExcessive = avgMovement < 60.0

        return notExcessive
    }

    // RELAXED criteria for movement regularity
    fun isMovementTooRegular(): Boolean {
        // Need significant history to make this judgment
        if (movementHistory.size < 12) return false

        // Calculate variance
        val avgMovement = movementHistory.average()
        var variance = 0.0
        for (movement in movementHistory) {
            variance += (movement - avgMovement) * (movement - avgMovement)
        }
        variance /= movementHistory.size

        // Only flag extremely regular movement with significant motion
        // This happens when sliding camera over printed images
        return avgMovement > 3.0 && variance < 0.005
    }

    private fun getSmoothedPosition(buffer: List<LandmarkPosition>): LandmarkPosition {
        // Use exponential weighted average instead of linear weights
        var sumX = 0f
        var sumY = 0f
        var sumWeights = 0f

        // Apply exponential weighting - more recent positions have much higher weight
        buffer.forEachIndexed { index, position ->
            val weight = Math.pow(1.5, index.toDouble()).toFloat()
            sumX += position.x * weight
            sumY += position.y * weight
            sumWeights += weight
        }

        return LandmarkPosition(
            sumX / sumWeights,
            sumY / sumWeights,
            buffer.last().visibility,
            buffer.last().timestamp
        )
    }

    fun reset() {
        landmarkBuffers.clear()
        smoothedPositions.clear()
        movementHistory.clear()
    }
}