package com.inputmapper.platform.root

import android.content.Context
import android.hardware.input.IInputManager
import android.os.IBinder
import android.os.SystemClock
import android.view.InputDevice
import android.view.InputEvent
import android.view.KeyEvent
import android.view.MotionEvent

/** Root-process InputManager injector. No shell or uinput fallback. */
internal class PrivilegedInputManagerEngine(private val maxPointers: Int) {
    private data class Pointer(var x: Float, var y: Float)

    private val pointers = linkedMapOf<Int, Pointer>()
    private var gestureDownTime = 0L
    private var manager: IInputManager? = null
    private var lastError: String? = null

    @Synchronized
    fun prepare(): String {
        if (maxPointers !in 1..32) {
            return fail("INVALID_ARGUMENT", "maxPointers=$maxPointers outside 1..32")
        }
        return try {
            val binder = inputServiceBinder()
                ?: return fail("INPUT_SERVICE", "Android input service binder is null")
            val proxy = IInputManager.Stub.asInterface(binder)
                ?: return fail("INPUT_SERVICE", "IInputManager.Stub.asInterface returned null")
            manager = proxy
            lastError = null
            "OK inputManager=${proxy.javaClass.name}"
        } catch (t: Throwable) {
            manager = null
            fail("PREPARE", describe(t))
        }
    }

    @Synchronized
    fun down(id: Int, x: Float, y: Float): String {
        validate(id, x, y)?.let { return it }
        if (pointers.containsKey(id)) return fail("POINTER_STATE", "pointer $id already down")
        if (pointers.size >= maxPointers) return fail("POINTER_LIMIT", "limit $maxPointers reached")

        val now = SystemClock.uptimeMillis()
        if (pointers.isEmpty()) gestureDownTime = now
        pointers[id] = Pointer(x, y)
        val reply = injectTouch(id, MotionEvent.ACTION_DOWN, now)
        if (!reply.startsWith("OK")) {
            pointers.remove(id)
            if (pointers.isEmpty()) gestureDownTime = 0L
        }
        return reply
    }

    @Synchronized
    fun move(id: Int, x: Float, y: Float): String {
        validate(id, x, y)?.let { return it }
        val pointer = pointers[id] ?: return fail("POINTER_STATE", "pointer $id not down")
        val oldX = pointer.x
        val oldY = pointer.y
        pointer.x = x
        pointer.y = y
        val reply = injectTouch(id, MotionEvent.ACTION_MOVE, SystemClock.uptimeMillis())
        if (!reply.startsWith("OK")) {
            pointer.x = oldX
            pointer.y = oldY
        }
        return reply
    }

    @Synchronized
    fun up(id: Int): String {
        if (id !in 0 until maxPointers) return fail("INVALID_ARGUMENT", "pointerId=$id outside range")
        if (!pointers.containsKey(id)) return fail("POINTER_STATE", "pointer $id not down")
        val reply = injectTouch(id, MotionEvent.ACTION_UP, SystemClock.uptimeMillis())
        if (reply.startsWith("OK")) {
            pointers.remove(id)
            if (pointers.isEmpty()) gestureDownTime = 0L
        }
        return reply
    }

    @Synchronized
    fun key(keyCode: Int, action: Int): String {
        if (action != KeyEvent.ACTION_DOWN && action != KeyEvent.ACTION_UP) {
            return fail("INVALID_ARGUMENT", "unsupported key action=$action")
        }
        val now = SystemClock.uptimeMillis()
        return inject(
            KeyEvent(
                now,
                now,
                action,
                keyCode,
                0,
                0,
                KeyEvent.KEYCODE_UNKNOWN,
                0,
                0,
                InputDevice.SOURCE_KEYBOARD
            )
        )
    }

    @Synchronized
    fun releaseAll(): String {
        var firstFailure: String? = null
        for (id in pointers.keys.toList().asReversed()) {
            val reply = injectTouch(id, MotionEvent.ACTION_UP, SystemClock.uptimeMillis())
            if (!reply.startsWith("OK") && firstFailure == null) firstFailure = reply
            pointers.remove(id)
        }
        gestureDownTime = 0L
        return firstFailure ?: "OK"
    }

    @Synchronized
    fun status(): String =
        "inputManagerPrepared=${manager != null} activePointers=${pointers.size} lastError=${lastError ?: "none"}"

    private fun inputServiceBinder(): IBinder? {
        val serviceManagerClass = Class.forName("android.os.ServiceManager")
        val getService = serviceManagerClass.getMethod("getService", String::class.java)
        return getService.invoke(null, Context.INPUT_SERVICE) as? IBinder
    }

    private fun injectTouch(target: Int, requested: Int, eventTime: Long): String {
        val ordered = pointers.entries.sortedBy { it.key }
        if (ordered.isEmpty()) return fail("POINTER_STATE", "no active pointers")
        val index = ordered.indexOfFirst { it.key == target }
        if (index < 0) return fail("POINTER_STATE", "pointer $target missing")

        val action = when (requested) {
            MotionEvent.ACTION_DOWN -> if (ordered.size == 1) {
                MotionEvent.ACTION_DOWN
            } else {
                MotionEvent.ACTION_POINTER_DOWN or (index shl MotionEvent.ACTION_POINTER_INDEX_SHIFT)
            }
            MotionEvent.ACTION_UP -> if (ordered.size == 1) {
                MotionEvent.ACTION_UP
            } else {
                MotionEvent.ACTION_POINTER_UP or (index shl MotionEvent.ACTION_POINTER_INDEX_SHIFT)
            }
            else -> MotionEvent.ACTION_MOVE
        }

        val properties = Array(ordered.size) { i ->
            MotionEvent.PointerProperties().apply {
                id = ordered[i].key
                toolType = MotionEvent.TOOL_TYPE_FINGER
            }
        }
        val coordinates = Array(ordered.size) { i ->
            MotionEvent.PointerCoords().apply {
                x = ordered[i].value.x
                y = ordered[i].value.y
                pressure = if (requested == MotionEvent.ACTION_UP && i == index) 0f else 1f
                size = 1f
            }
        }

        val event = MotionEvent.obtain(
            gestureDownTime,
            eventTime,
            action,
            ordered.size,
            properties,
            coordinates,
            0,
            0,
            1f,
            1f,
            -1,
            0,
            InputDevice.SOURCE_TOUCHSCREEN,
            0
        )
        return try {
            inject(event)
        } finally {
            event.recycle()
        }
    }

    private fun inject(event: InputEvent): String {
        val inputManager = manager ?: return fail("NOT_READY", "InputManager backend not prepared")
        return try {
            // Mode 0 is INJECT_INPUT_EVENT_MODE_ASYNC in Android's InputManager contract.
            if (inputManager.injectInputEvent(event, 0)) {
                "OK"
            } else {
                fail("REJECTED", "Android InputManager rejected ${event.javaClass.simpleName}")
            }
        } catch (t: Throwable) {
            fail("INJECT", describe(t))
        }
    }

    private fun validate(id: Int, x: Float, y: Float): String? {
        if (id !in 0 until maxPointers) return fail("INVALID_ARGUMENT", "pointerId=$id outside range")
        if (!x.isFinite() || !y.isFinite() || x < 0f || y < 0f) {
            return fail("INVALID_ARGUMENT", "invalid coordinates ($x,$y)")
        }
        return null
    }

    private fun fail(code: String, message: String): String {
        lastError = "$code $message"
        return "ERROR $code $message"
    }

    private fun describe(t: Throwable): String {
        val cause = if (t is java.lang.reflect.InvocationTargetException) t.cause ?: t else t
        return "${cause.javaClass.simpleName}: ${cause.message ?: "unknown"}"
    }
}
