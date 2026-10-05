package com.example.injector

import android.graphics.PointF
import android.util.Log
import com.example.model.PrivilegeMethod

/**
 * APatch detection exists, but its device-specific injection transport has not
 * been verified on hardware. This backend therefore fails closed instead of
 * pretending generic su shell commands are an APatch implementation.
 */
class APatchInjector : InputInjector {
    override val method: PrivilegeMethod = PrivilegeMethod.APATCH

    override fun isAvailable(): Boolean = false

    override fun injectTap(x: Float, y: Float): Boolean = unavailable("tap")

    override fun injectDrag(path: List<PointF>, durationMs: Long): Boolean = unavailable("drag")

    override fun injectKeyEvent(keyCode: Int, action: Int): Boolean = unavailable("key")

    override fun cleanup() {
        Log.d(TAG, "APatch injector cleanup: no session was created")
    }

    private fun unavailable(operation: String): Boolean {
        Log.e(
            TAG,
            "APatch $operation refused: the APatch injection transport has not been hardware-verified. No fallback was used."
        )
        return false
    }

    private companion object {
        const val TAG = "NexusAPatch"
    }
}
