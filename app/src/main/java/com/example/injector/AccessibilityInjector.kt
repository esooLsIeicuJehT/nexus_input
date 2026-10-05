package com.example.injector

import android.graphics.PointF
import android.util.Log
import com.example.model.PrivilegeMethod
import com.example.service.ControlystAccessibilityService

class AccessibilityInjector : InputInjector {
    override val method: PrivilegeMethod = PrivilegeMethod.ACCESSIBILITY

    override fun isAvailable(): Boolean = ControlystAccessibilityService.isServiceRunning()

    override fun injectTap(x: Float, y: Float): Boolean {
        val service = ControlystAccessibilityService.getInstance()
        return if (service != null) {
            service.performTap(x, y)
        } else {
            Log.w(TAG, "Accessibility service is not active for tap ($x, $y)")
            false
        }
    }

    override fun injectDrag(path: List<PointF>, durationMs: Long): Boolean {
        val service = ControlystAccessibilityService.getInstance()
        return if (service != null) {
            service.performDrag(path, durationMs)
        } else {
            Log.w(TAG, "Accessibility service is not active for drag")
            false
        }
    }

    override fun injectKeyEvent(keyCode: Int, action: Int): Boolean {
        Log.e(
            TAG,
            "Arbitrary key injection is not supported by the Accessibility backend. keyCode=$keyCode action=$action"
        )
        return false
    }

    override fun cleanup() {
        val service=ControlystAccessibilityService.getInstance()
        check(service != null && service.awaitGestureIdle()) { "Accessibility gesture completion could not be confirmed; Android may still be executing a gesture" }
        Log.d(TAG,"Accessibility gesture callbacks confirmed idle")
    }

    private companion object {
        const val TAG = "NexusAccessibility"
    }
}
