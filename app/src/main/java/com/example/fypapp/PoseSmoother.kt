package com.example.fypapp

import com.google.mlkit.vision.pose.Pose
import com.google.mlkit.vision.pose.PoseLandmark
import android.graphics.PointF

class PoseSmoother {
    private val bufferSize = 5
    private val landmarkBuffers = mutableMapOf<Int, MutableList<LandmarkPosition>>()
    private val smoothedPositions = mutableMapOf<Int, PointF>()

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

        return pose // Return original pose since we'll use smoothedPositions separately
    }

    fun getSmoothPosition(landmarkType: Int): PointF? {
        return smoothedPositions[landmarkType]
    }

    private fun getSmoothedPosition(buffer: List<LandmarkPosition>): LandmarkPosition {
        var sumX = 0f
        var sumY = 0f
        var sumWeights = 0f

        buffer.forEachIndexed { index, position ->
            val weight = (index + 1f) / buffer.size
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
    }
}