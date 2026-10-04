package com.example.injector

import android.graphics.PointF
import com.example.model.PrivilegeMethod

interface InputInjector {
    val method: PrivilegeMethod

    fun isAvailable(): Boolean

    /**
     * Establish backend resources before gameplay begins. Backends that do not
     * need a connection may rely on the default availability check.
     */
    fun prepare(): Boolean = isAvailable()

    fun injectTap(x: Float, y: Float): Boolean

    fun injectDrag(path: List<PointF>, durationMs: Long): Boolean

    fun injectKeyEvent(keyCode: Int, action: Int): Boolean

    /** Persistent touch contacts used by virtual sticks and held controls. */
    fun beginTouch(pointerId: Int, x: Float, y: Float): Boolean = false

    fun moveTouch(pointerId: Int, x: Float, y: Float): Boolean = false

    fun endTouch(pointerId: Int): Boolean = false

    fun releaseAll() {
        cleanup()
    }

    fun cleanup()
}
