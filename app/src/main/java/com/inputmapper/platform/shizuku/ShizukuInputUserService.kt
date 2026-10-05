package com.inputmapper.platform.shizuku

import android.content.Context
import android.os.Process
import android.view.InputDevice
import android.view.InputEvent
import android.view.KeyEvent
import android.view.MotionEvent

/**
 * Runs inside a Shizuku UserService process. With an ADB-started Shizuku server this process
 * runs as shell UID 2000; with root-started Shizuku/Sui it can run as UID 0.
 */
class ShizukuInputUserService() : IShizukuInputService.Stub() {
    private val inputManager: Any
    private val injectMethod: java.lang.reflect.Method

    init {
        val pair = resolveInputManager()
        inputManager = pair.first
        injectMethod = pair.second
    }

    @Suppress("UNUSED_PARAMETER")
    constructor(context: Context) : this()

    override fun readSurfaceLayers(): String = com.inputmapper.platform.core.SurfaceFrameProbe.layers()
    override fun readSurfaceLatency(layer: String): String = com.inputmapper.platform.core.SurfaceFrameProbe.latency(layer)

    override fun selfTest(): String = "OK uid=${Process.myUid()} pid=${Process.myPid()}"

    override fun injectTouch(
        action: Int,
        downTime: Long,
        eventTime: Long,
        pointerCount: Int,
        pointerIds: IntArray,
        xs: FloatArray,
        ys: FloatArray
    ): String {
        if (pointerCount <= 0 || pointerIds.size != pointerCount || xs.size != pointerCount || ys.size != pointerCount) {
            return "ERROR INVALID_ARGUMENT pointer arrays do not match pointerCount=$pointerCount"
        }
        return try {
            val properties = Array(pointerCount) { index ->
                MotionEvent.PointerProperties().apply {
                    id = pointerIds[index]
                    toolType = MotionEvent.TOOL_TYPE_FINGER
                }
            }
            val coords = Array(pointerCount) { index ->
                MotionEvent.PointerCoords().apply {
                    x = xs[index]
                    y = ys[index]
                    pressure = 1.0f
                    size = 1.0f
                }
            }
            val event = MotionEvent.obtain(
                downTime,
                eventTime,
                action,
                pointerCount,
                properties,
                coords,
                0,
                0,
                1.0f,
                1.0f,
                0,
                0,
                InputDevice.SOURCE_TOUCHSCREEN,
                0
            )
            try {
                if (injectInputEvent(event)) "OK" else "ERROR INJECTION_REJECTED InputManager.injectInputEvent returned false"
            } finally {
                event.recycle()
            }
        } catch (t: Throwable) {
            "ERROR ${t.javaClass.simpleName} ${t.message ?: "unknown"}"
        }
    }

    override fun injectKey(
        keyCode: Int,
        action: Int,
        downTime: Long,
        eventTime: Long,
        metaState: Int,
        repeatCount: Int
    ): String {
        if (action != KeyEvent.ACTION_DOWN && action != KeyEvent.ACTION_UP) {
            return "ERROR INVALID_ARGUMENT unsupported key action=$action"
        }
        return try {
            val event = KeyEvent(
                downTime,
                eventTime,
                action,
                keyCode,
                repeatCount,
                metaState,
                -1,
                0,
                0,
                InputDevice.SOURCE_KEYBOARD
            )
            if (injectInputEvent(event)) "OK" else "ERROR INJECTION_REJECTED InputManager.injectInputEvent returned false"
        } catch (t: Throwable) {
            "ERROR ${t.javaClass.simpleName} ${t.message ?: "unknown"}"
        }
    }

    private fun injectInputEvent(event: InputEvent): Boolean {
        val result = injectMethod.invoke(inputManager, event, 0)
        return result as? Boolean ?: false
    }

    override fun destroy() {
        System.exit(0)
    }

    private fun resolveInputManager(): Pair<Any, java.lang.reflect.Method> {
        val clazz = Class.forName("android.hardware.input.InputManager")
        val getInstance = clazz.getDeclaredMethod("getInstance").apply { isAccessible = true }
        val instance = getInstance.invoke(null) ?: error("InputManager.getInstance returned null")
        val inject = clazz.getDeclaredMethod(
            "injectInputEvent",
            InputEvent::class.java,
            Int::class.javaPrimitiveType
        ).apply { isAccessible = true }
        return instance to inject
    }
}
