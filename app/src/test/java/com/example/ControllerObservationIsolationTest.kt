package com.example

import android.app.Application
import android.hardware.input.InputManager
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import androidx.test.core.app.ApplicationProvider
import com.example.input.ControllerInputMonitor
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.InputDeviceBuilder

/** Explicit Android event fixtures; this does not verify two physical controllers. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ControllerObservationIsolationTest {
    @Before fun registerDistinctDevices() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val manager = app.getSystemService(InputManager::class.java)
        for ((id, axis) in listOf(71 to MotionEvent.AXIS_X, 72 to MotionEvent.AXIS_Y)) {
            val device = InputDeviceBuilder.newBuilder().setId(id).setName("Explicit controller fixture $id")
                .setDescriptor("observation-fixture-$id")
                .setSources(InputDevice.SOURCE_JOYSTICK or InputDevice.SOURCE_GAMEPAD)
                .addMotionRange(axis, InputDevice.SOURCE_JOYSTICK, -1f, 1f, 0f, 0f, 0f).build()
            shadowOf(manager).addInputDevice(device)
            ControllerInputMonitor.onDeviceRemoved(id)
        }
    }

    private fun motion(id: Int, axis: Int, value: Float, at: Long) = MotionEvent.obtain(0, at,
        MotionEvent.ACTION_MOVE, 1, arrayOf(MotionEvent.PointerProperties().apply { this.id = 0 }),
        arrayOf(MotionEvent.PointerCoords().apply { setAxisValue(axis, value) }),
        0, 0, 1f, 1f, id, 0, InputDevice.SOURCE_JOYSTICK, 0).also {
        shadowOf(it).setDevice(InputDevice.getDevice(id))
    }

    private fun key(id: Int, code: Int, at: Long) = KeyEvent(at, at, KeyEvent.ACTION_DOWN,
        code, 0, 0, id, code, 0, InputDevice.SOURCE_GAMEPAD)

    @Test fun secondControllerKeyDoesNotInheritFirstControllerAxes() {
        val first = motion(71, MotionEvent.AXIS_X, .75f, 910)
        ControllerInputMonitor.onMotionEvent(first); first.recycle()
        assertEquals(.75f, ControllerInputMonitor.state.value.axes["LX"]!!, 0f)
        ControllerInputMonitor.onKeyEvent(key(72, KeyEvent.KEYCODE_BUTTON_A, 920))
        val second = ControllerInputMonitor.state.value
        assertEquals(72, second.deviceId)
        assertTrue(second.axes.isEmpty())
        assertTrue(second.normalizedAxes.isEmpty())
        assertEquals(setOf("BUTTON_A"), second.pressedButtons)
        assertTrue(second.eventLog.any { "dev=71" in it })
        assertTrue(second.eventLog.last().contains("dev=72"))
    }

    @Test fun secondControllerMotionDoesNotInheritFirstControllerButtonsOrLearningPress() {
        ControllerInputMonitor.onKeyEvent(key(71, KeyEvent.KEYCODE_BUTTON_B, 930))
        assertNotNull(ControllerInputMonitor.state.value.lastPress)
        val second = motion(72, MotionEvent.AXIS_Y, -.5f, 940)
        ControllerInputMonitor.onMotionEvent(second); second.recycle()
        val observed = ControllerInputMonitor.state.value
        assertEquals(72, observed.deviceId)
        assertTrue(observed.pressedButtons.isEmpty())
        assertNull(observed.lastPress)
        assertFalse(observed.axes.containsKey("LX"))
        assertEquals(-.5f, observed.axes["LY"]!!, 0f)
    }

    @Test fun sameControllerKeyPreservesActuallyObservedAxes() {
        val first = motion(71, MotionEvent.AXIS_X, .25f, 950)
        ControllerInputMonitor.onMotionEvent(first); first.recycle()
        ControllerInputMonitor.onKeyEvent(key(71, KeyEvent.KEYCODE_BUTTON_X, 960))
        assertEquals(.25f, ControllerInputMonitor.state.value.axes["LX"]!!, 0f)
    }
}
