package com.example.injector

import android.graphics.PointF
import com.example.model.PrivilegeMethod

interface InputInjector {
    val method: PrivilegeMethod

    fun isAvailable(): Boolean

    fun injectTap(x: Float, y: Float): Boolean

    fun injectDrag(path: List<PointF>, durationMs: Long): Boolean

    fun injectKeyEvent(keyCode: Int, action: Int): Boolean

    fun releaseAll() {
        cleanup()
    }

    fun cleanup()
}
