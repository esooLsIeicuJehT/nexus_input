package com.inputmapper.platform.shizuku

import android.content.Context
import android.os.Build
import android.os.Process
import android.view.InputDevice
import android.view.InputEvent
import android.view.KeyEvent
import android.view.MotionEvent

/**
 * Runs inside a Shizuku UserService process. With an ADB-started Shizuku server this process
 * runs as shell UID 2000; with root-started Shizuku/Sui it can run as UID 0.
 *
 * Android 14+ must not resolve input injection through InputManager.getInstance(). Modern
 * Android implements that legacy accessor through ActivityThread.currentApplication(), while a
 * Shizuku UserService is not a normal application process. InputManagerGlobal talks directly to
 * the input service and is the process-safe framework path on API 34+.
 */
class ShizukuInputUserService() : IShizukuInputService.Stub() {
    private data class InputBridge(
        val target: Any,
        val injectMethod: java.lang.reflect.Method,
        val name: String
    )

    private val inputBridge: InputBridge = resolveInputBridge()

    @Suppress("UNUSED_PARAMETER")
    constructor(context: Context) : this()

    override fun readSurfaceLayers(): String = com.inputmapper.platform.core.SurfaceFrameProbe.layers()
    override fun readSurfaceLatency(layer: String): String = com.inputmapper.platform.core.SurfaceFrameProbe.latency(layer)

    override fun selfTest(): String =
        "OK uid=${Process.myUid()} pid=${Process.myPid()} bridge=${inputBridge.name} sdk=${Build.VERSION.SDK_INT}"

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
                if (injectInputEvent(event)) "OK" else "ERROR INJECTION_REJECTED ${inputBridge.name}.injectInputEvent returned false"
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
            if (injectInputEvent(event)) "OK" else "ERROR INJECTION_REJECTED ${inputBridge.name}.injectInputEvent returned false"
        } catch (t: Throwable) {
            "ERROR ${t.javaClass.simpleName} ${t.message ?: "unknown"}"
        }
    }

    private fun injectInputEvent(event: InputEvent): Boolean {
        val result = inputBridge.injectMethod.invoke(inputBridge.target, event, 0)
        return result as? Boolean ?: false
    }

    override fun destroy() {
        System.exit(0)
    }

    private fun resolveInputBridge(): InputBridge {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            resolveInputManagerGlobal()
        } else {
            resolveLegacyInputManager()
        }
    }

    /**
     * API 34+ framework contract. InputManagerGlobal.getInstance() resolves the input binder
     * directly through ServiceManager and does not require ActivityThread.currentApplication().
     */
    private fun resolveInputManagerGlobal(): InputBridge {
        val clazz = Class.forName("android.hardware.input.InputManagerGlobal")
        val getInstance = clazz.getDeclaredMethod("getInstance").apply { isAccessible = true }
        val instance = getInstance.invoke(null) ?: error("InputManagerGlobal.getInstance returned null")
        val inject = clazz.getDeclaredMethod(
            "injectInputEvent",
            InputEvent::class.java,
            Int::class.javaPrimitiveType
        ).apply { isAccessible = true }
        return InputBridge(instance, inject, "InputManagerGlobal")
    }

    /**
     * Pre-Android-14 compatibility path. Older framework builds expose InputManager.getInstance()
     * without the modern ActivityThread.currentApplication() dependency.
     */
    private fun resolveLegacyInputManager(): InputBridge {
        val clazz = Class.forName("android.hardware.input.InputManager")
        val getInstance = clazz.getDeclaredMethod("getInstance").apply { isAccessible = true }
        val instance = getInstance.invoke(null) ?: error("InputManager.getInstance returned null")
        val inject = clazz.getDeclaredMethod(
            "injectInputEvent",
            InputEvent::class.java,
            Int::class.javaPrimitiveType
        ).apply { isAccessible = true }
        return InputBridge(instance, inject, "InputManager")
    }
}
