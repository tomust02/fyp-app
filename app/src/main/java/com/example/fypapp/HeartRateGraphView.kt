package com.example.fypapp

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.util.Log
import android.view.View

class HeartRateGraphView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val dataPoints = mutableListOf<DataPoint>()
    private val paintLine = Paint().apply {
        color = Color.RED // Default color
        strokeWidth = 5f
        style = Paint.Style.STROKE
    }
    private val paintAxis = Paint().apply {
        color = Color.BLACK
        strokeWidth = 3f
        style = Paint.Style.STROKE
    }
    private val paintText = Paint().apply {
        color = Color.BLACK
        textSize = 30f
        textAlign = Paint.Align.RIGHT // Align text to the right for y-axis labels
    }

    private var yMin = 50f
    private var yMax = 200f
    private val windowSize = 30 // Number of data points to display (e.g., last 30 points)

    data class DataPoint(val index: Float, val value: Float)

    fun setLineColor(color: Int) {
        paintLine.color = color
        invalidate()
    }

    fun setYRange(min: Float, max: Float) {
        yMin = min
        yMax = max
        invalidate()
    }

    fun addDataPoint(index: Float, value: Float) {
        dataPoints.add(DataPoint(index, value))
        // Keep only the last 30 data points
        dataPoints.removeAll { it.index < dataPoints.last().index - windowSize }
        Log.d("HeartRateGraphView", "Added data point: index=$index, value=$value, total points=${dataPoints.size}")
        invalidate() // Redraw the view
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        if (dataPoints.isEmpty()) {
            Log.d("HeartRateGraphView", "No data points to draw")
            canvas.drawText("No data available", 50f, height / 2f, paintText)
            return
        }

        // Increased padding to make the graph smaller and create space for labels
        val paddingLeft = 100f // Increased left padding for y-axis labels
        val paddingRight = 50f // Right padding
        val paddingTop = 80f // Increased top padding for "Value" label
        val paddingBottom = 80f // Increased bottom padding for x-axis labels
        val graphWidth = width - paddingLeft - paddingRight
        val graphHeight = height - paddingTop - paddingBottom

        // Draw axes
        canvas.drawLine(paddingLeft, height - paddingBottom, width - paddingRight, height - paddingBottom, paintAxis) // X-axis
        canvas.drawLine(paddingLeft, height - paddingBottom, paddingLeft, paddingTop, paintAxis) // Y-axis

        // Draw axis labels
        // "Index" label (x-axis title)
        canvas.drawText("Index", width / 2f, height - 20f, paintText.apply { textAlign = Paint.Align.CENTER })
        // "Value" label (y-axis title)
        canvas.drawText("Value", 20f, paddingTop - 30f, paintText.apply { textAlign = Paint.Align.LEFT })

        // Draw Y-axis labels based on yMin and yMax
        val yRange = yMax - yMin
        val step = yRange / 4
        for (i in 0..4) {
            val value = yMin + i * step
            val y = height - paddingBottom - (value - yMin) * graphHeight / yRange
            // Draw y-axis labels further to the left to avoid overlap
            canvas.drawText(value.toInt().toString(), paddingLeft - 20f, y + 10f, paintText)
            Log.d("HeartRateGraphView", "Drawing y-axis label: $value at y=$y")
        }

        // Draw X-axis labels (0 to 30, left to right)
        val latestIndex = dataPoints.last().index
        val minIndex = latestIndex - windowSize
        for (relativeIndex in 0..windowSize step 5) {
            val x = paddingLeft + relativeIndex * graphWidth / windowSize
            val label = relativeIndex.toString()
            // Draw x-axis labels further below to avoid overlap
            canvas.drawText(label, x, height - paddingBottom + 50f, paintText.apply { textAlign = Paint.Align.CENTER })
            Log.d("HeartRateGraphView", "Drawing x-axis label: $label at x=$x")
        }

        // Draw the graph
        Log.d("HeartRateGraphView", "Drawing graph with ${dataPoints.size} points")
        for (i in 0 until dataPoints.size - 1) {
            val point1 = dataPoints[i]
            val point2 = dataPoints[i + 1]

            // Map index to x-coordinate (0 to 30, left to right)
            val x1 = paddingLeft + (point1.index - minIndex) * graphWidth / windowSize
            val x2 = paddingLeft + (point2.index - minIndex) * graphWidth / windowSize

            // Map value to y-coordinate (custom range)
            val y1 = height - paddingBottom - (point1.value - yMin) * graphHeight / yRange
            val y2 = height - paddingBottom - (point2.value - yMin) * graphHeight / yRange

            Log.d("HeartRateGraphView", "Drawing line from ($x1, $y1) to ($x2, $y2)")
            canvas.drawLine(x1, y1, x2, y2, paintLine)
        }
    }
}