package com.example.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.PointF
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityEvent

class ControlystAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "ControlystA11y"
        private var instance: ControlystAccessibilityService? = null

        fun getInstance(): ControlystAccessibilityService? = instance

        fun isServiceRunning(): Boolean = instance != null
    }

    private val mainHandler = Handler(Looper.getMainLooper())

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        Log.i(TAG, "Controlyst Accessibility Service Connected successfully")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Listening for window state changed if needed for game foreground tracking
    }

    override fun onInterrupt() {
        Log.w(TAG, "Controlyst Accessibility Service Interrupted")
    }

    override fun onDestroy() {
        super.onDestroy()
        if (instance == this) {
            instance = null
        }
    }

    /**
     * Injects synthetic tap at exact screen coordinates (x, y)
     */
    fun performTap(x: Float, y: Float, durationMs: Long = 50L): Boolean {
        val path = Path().apply {
            moveTo(x, y)
        }
        val stroke = GestureDescription.StrokeDescription(path, 0, durationMs)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        return dispatchGesture(gesture, null, mainHandler)
    }

    /**
     * Injects synthetic drag/swipe along path points
     */
    fun performDrag(points: List<PointF>, durationMs: Long): Boolean {
        if (points.isEmpty()) return false
        val path = Path().apply {
            moveTo(points[0].x, points[0].y)
            for (i in 1 until points.size) {
                lineTo(points[i].x, points[i].y)
            }
        }
        val safeDuration = durationMs.coerceAtLeast(40L)
        val stroke = GestureDescription.StrokeDescription(path, 0, safeDuration)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        return dispatchGesture(gesture, null, mainHandler)
    }
}
