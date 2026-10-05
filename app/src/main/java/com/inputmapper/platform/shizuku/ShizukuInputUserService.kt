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
class ShizukuInputUserService internal constructor(
    private val inputBridge: Result<FrameworkInputBridge>
) : IShizukuInputService.Stub() {
    // Keep resolver failures observable through selfTest instead of losing the process before
    // its binder can explain why initialization failed.
    constructor() : this(runCatching { FrameworkInputBridge.resolve() })

    @Suppress("UNUSED_PARAMETER")
    constructor(context: Context) : this()

    override fun readSurfaceLayers(): String = com.inputmapper.platform.core.SurfaceFrameProbe.layers()
    override fun readSurfaceLatency(layer: String): String = com.inputmapper.platform.core.SurfaceFrameProbe.latency(layer)

    override fun selfTest(): String = inputBridge.fold(
        onSuccess = { bridge ->
            "OK uid=${Process.myUid()} pid=${Process.myPid()} bridge=${bridge.name} " +
                "sdk=${Build.VERSION.SDK_INT} mode=WAIT_FOR_RESULT(1) probe=BRIDGE_ONLY serviceVersion=${ShizukuInjector.USER_SERVICE_VERSION}"
        },
        onFailure = { error -> "ERROR BRIDGE_INIT sdk=${Build.VERSION.SDK_INT} ${failureDescription(error)}" }
    )

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
                injectInputEvent(event)
            } finally {
                event.recycle()
            }
        } catch (t: Throwable) {
            "ERROR ${failureDescription(t)}"
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
            injectInputEvent(event)
        } catch (t: Throwable) {
            "ERROR ${failureDescription(t)}"
        }
    }

    private fun injectInputEvent(event: InputEvent): String {
        val bridge = inputBridge.getOrThrow()
        return if (bridge.inject(event)) "OK"
        else "ERROR INJECTION_REJECTED ${bridge.name}.injectInputEvent WAIT_FOR_RESULT returned false; check focus, touch occlusion and InputDispatcher logs"
    }

    private fun failureDescription(error: Throwable): String =
        "${error.javaClass.simpleName}: ${error.message ?: "<no message>"}"

    override fun destroy() {
        System.exit(0)
    }

}
