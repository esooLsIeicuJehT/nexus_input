package com.example

import android.app.Application
import android.hardware.input.InputManager
import android.view.*
import androidx.test.core.app.ApplicationProvider
import com.example.calibration.CalibrationManager
import com.example.input.ControllerInputMonitor
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.InputDeviceBuilder
import org.robolectric.annotation.Config

/** Android InputDevice/MotionEvent fixtures only; no physical controller verification is implied. */
@RunWith(RobolectricTestRunner::class) @Config(sdk=[34])
class ControllerInputTest {
    @Test fun testerResetCannotHideCapturedControllerRemoval() {
        val devices=com.example.input.ControllerSessionDevices()
        devices.record(41);devices.record(42)
        com.example.input.ControllerInputMonitor.onDeviceRemoved(41)
        assertTrue(devices.remove(41));assertFalse(devices.remove(41))
        assertTrue(devices.remove(42));devices.record(43);devices.clear()
        assertFalse(devices.remove(43))
    }

    private fun device(): Application {
        val app=ApplicationProvider.getApplicationContext<Application>()
        val device=InputDeviceBuilder.newBuilder().setId(41).setName("Test fixture gamepad").setDescriptor("fixture")
            .setSources(InputDevice.SOURCE_JOYSTICK or InputDevice.SOURCE_GAMEPAD)
            .addMotionRange(MotionEvent.AXIS_X,InputDevice.SOURCE_JOYSTICK,-1f,1f,0f,0f,0f)
            .addMotionRange(MotionEvent.AXIS_Y,InputDevice.SOURCE_JOYSTICK,-1f,1f,0f,0f,0f)
            .addMotionRange(MotionEvent.AXIS_HAT_X,InputDevice.SOURCE_JOYSTICK,-1f,1f,0f,0f,0f).build()
        shadowOf(app.getSystemService(InputManager::class.java)).addInputDevice(device)
        return app
    }
    private fun motion(x:Float,y:Float)=MotionEvent.obtain(0,10,MotionEvent.ACTION_MOVE,1,
        arrayOf(MotionEvent.PointerProperties().apply { id=0;toolType=MotionEvent.TOOL_TYPE_UNKNOWN }),
        arrayOf(MotionEvent.PointerCoords().apply { setAxisValue(MotionEvent.AXIS_X,x);setAxisValue(MotionEvent.AXIS_Y,y);setAxisValue(MotionEvent.AXIS_HAT_X,-1f) }),
        0,0,1f,1f,41,0,InputDevice.SOURCE_JOYSTICK,0)
    @Test fun testerUsesActualDeliveredAxesAndClearsTheRemovedDevice() {
        device();val event=motion(.25f,-.5f)
        ControllerInputMonitor.onMotionEvent(event);event.recycle()
        assertEquals("Test fixture gamepad",ControllerInputMonitor.state.value.connectedEventSource)
        assertEquals(.25f,ControllerInputMonitor.state.value.axes["LX"]!!,0f)
        assertEquals(-1f,ControllerInputMonitor.state.value.axes["HAT_X"]!!,0f)
        assertFalse(ControllerInputMonitor.state.value.axes.containsKey("RX"))
        ControllerInputMonitor.onDeviceRemoved(999);assertFalse(ControllerInputMonitor.state.value.axes.isEmpty())
        ControllerInputMonitor.onDeviceRemoved(41);assertTrue(ControllerInputMonitor.state.value.axes.isEmpty())
        assertNull(ControllerInputMonitor.state.value.connectedEventSource)
    }
    @Test fun actualMotionEventsDriveBothCalibrationPhases() = runBlocking {
        val manager=CalibrationManager(device())
        val calibration=async(Dispatchers.Default) { manager.runStickCalibration {} }
        withTimeout(10000) {
            while(!calibration.isCompleted) {
                val x=if(manager.stickState.value.phase.startsWith("EXTENSION")) .9f else .02f
                val event=motion(x,0f);manager.onMotionEvent(event);event.recycle();delay(40)
            }
        }
        assertTrue(calibration.await());assertTrue(manager.stickState.value.isMeasured)
        assertEquals(.04f,manager.stickState.value.computedInnerDeadzone,.0001f)
        assertEquals(.9f,manager.stickState.value.computedOuterDeadzone,.0001f)
        assertTrue(manager.stickState.value.restSamples.size>=5);assertTrue(manager.stickState.value.maxSamples.size>=5)
    }
}
