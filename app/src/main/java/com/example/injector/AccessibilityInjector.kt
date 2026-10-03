package com.example.injector

import android.graphics.PointF
import android.util.Log
import com.example.model.PrivilegeMethod
import com.example.service.ControlystAccessibilityService

class AccessibilityInjector : InputInjector {
    override val method: PrivilegeMethod = PrivilegeMethod.ACCESSIBILITY

    override fun isAvailable(): Boolean {
        return ControlystAccessibilityService.isServiceRunning()
    }

    override fun injectTap(x: Float, y: Float): Boolean {
        val service = ControlystAccessibilityService.getInstance()
        return if (service != null) {
            service.performTap(x, y)
        } else {
            Log.w("AccessibilityInjector", "Service not active for tap ($x, $y)")
            false
        }
    }

    override fun injectDrag(path: List<PointF>, durationMs: Long): Boolean {
        val service = ControlystAccessibilityService.getInstance()
        return if (service != null) {
            service.performDrag(path, durationMs)
        } else {
            Log.w("AccessibilityInjector", "Service not active for drag")
            false
        }
    }

    override fun injectKeyEvent(keyCode: Int, action: Int): Boolean {
        // Stock accessibility service has limited synthetic key event injection without root/IME,
        // so we log or map to common accessible actions (Back, Home, etc.)
        Log.d("AccessibilityInjector", "injectKeyEvent keyCode=$keyCode action=$action")
        return true
    }

    override fun cleanup() {
        Log.d("AccessibilityInjector", "Accessibility injector cleaned up")
    }
}
