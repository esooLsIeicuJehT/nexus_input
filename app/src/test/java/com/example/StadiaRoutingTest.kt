package com.example

import android.app.Application
import android.graphics.PointF
import android.hardware.input.InputManager
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import androidx.test.core.app.ApplicationProvider
import com.example.input.ControllerBindingAliases
import com.example.input.ControllerInputMonitor
import com.example.input.ControllerRoutingTrace
import com.example.input.GamepadMappingRuntime
import com.example.input.ProfileValidator
import com.example.injector.InputInjector
import com.example.model.*
import java.util.concurrent.CopyOnWriteArrayList
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.InputDeviceBuilder

/** Replays the user's captured identities. Fixtures do not establish in-game acceptance. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class StadiaRoutingTest {
    private fun device(stadia: Boolean = true): InputDevice {
        val device = InputDeviceBuilder.newBuilder().setId(77).setName("Stadia fixture")
            .setDescriptor("stadia-routing-fixture").setVendorId(if (stadia) 6353 else 1)
            .setProductId(37888).setSources(InputDevice.SOURCE_GAMEPAD or InputDevice.SOURCE_JOYSTICK)
            .addMotionRange(MotionEvent.AXIS_X, InputDevice.SOURCE_JOYSTICK, -1f, 1f, 0f, 0f, 0f)
            .addMotionRange(MotionEvent.AXIS_Y, InputDevice.SOURCE_JOYSTICK, -1f, 1f, 0f, 0f, 0f)
            .addMotionRange(MotionEvent.AXIS_HAT_X, InputDevice.SOURCE_JOYSTICK, -1f, 1f, 0f, 0f, 0f)
            .addMotionRange(MotionEvent.AXIS_HAT_Y, InputDevice.SOURCE_JOYSTICK, -1f, 1f, 0f, 0f, 0f).build()
        shadowOf(ApplicationProvider.getApplicationContext<Application>()
            .getSystemService(InputManager::class.java)).addInputDevice(device)
        ControllerInputMonitor.onDeviceRemoved(77)
        return device
    }

    private fun key(device: InputDevice, code: Int, scan: Int, action: Int = KeyEvent.ACTION_DOWN) =
        KeyEvent(100, 120, action, code, 0, 0, device.id, scan, 0, 0x501)
            .also { shadowOf(it).setDevice(device) }

    private fun coords(x: Float, y: Float) = MotionEvent.PointerCoords().apply {
        setAxisValue(MotionEvent.AXIS_HAT_X, x); setAxisValue(MotionEvent.AXIS_HAT_Y, y)
    }
    private fun motion(device: InputDevice, x: Float, y: Float) = MotionEvent.obtain(
        100, 120, MotionEvent.ACTION_MOVE, 1,
        arrayOf(MotionEvent.PointerProperties().apply { id = 0 }), arrayOf(coords(x, y)),
        0, 0, 1f, 1f, device.id, 0, InputDevice.SOURCE_JOYSTICK, 0
    ).also { shadowOf(it).setDevice(device) }

    private class Recording(override val method: PrivilegeMethod) : InputInjector {
        val calls = CopyOnWriteArrayList<Pair<String, Int>>()
        override fun isAvailable() = true
        override fun injectTap(x: Float, y: Float) = false
        override fun injectDrag(path: List<PointF>, durationMs: Long) = false
        override fun injectKeyEvent(keyCode: Int, action: Int) = false
        override fun beginTouch(pointerId: Int, x: Float, y: Float): Boolean { calls += "down" to pointerId; return true }
        override fun moveTouch(pointerId: Int, x: Float, y: Float) = true
        override fun endTouch(pointerId: Int): Boolean { calls += "up" to pointerId; return true }
        override fun cleanup() = Unit
    }
    private fun config(nodes: List<MappingNode>) = MappingConfig(
        id = "stadia", profileName = "Stadia", gamePackage = "com.test.game", buttons = nodes)

    @Test fun capturedFiveCenterKeysAndHatDiagonalReachIndependentRootAndShizukuContacts() {
        val device = device()
        val controls = listOf(Triple("SELECT", 109, 314), Triple("START", 108, 315),
            Triple("GUIDE", 110, 316), Triple("ASSISTANT", 0, 704), Triple("CAPTURE", 0, 705))
        val profile = config(controls.mapIndexed { index, (label, _, _) ->
            MappingNode(label, .3f, .4f, boundKey = label, buttonBehavior = ButtonBehavior.HOLD, touchSlot = index + 20)
        } + listOf(MappingNode("left", .2f, .2f, boundKey = "DPAD_LEFT", buttonBehavior = ButtonBehavior.HOLD, touchSlot = 25),
            MappingNode("up", .2f, .1f, boundKey = "DPAD_UP", buttonBehavior = ButtonBehavior.HOLD, touchSlot = 26)))
        assertTrue(ProfileValidator.errors(profile).toString(), ProfileValidator.errors(profile).isEmpty())
        for (method in listOf(PrivilegeMethod.KERNELSU, PrivilegeMethod.SHIZUKU)) {
            val backend = Recording(method)
            val runtime = GamepadMappingRuntime({ 1604 to 720 }) { fail(it) }
            try {
                controls.forEach { (label, code, scan) ->
                    assertTrue("$label must match", runtime.handleKeyEvent(key(device, code, scan), profile, backend))
                }
                motion(device, -1f, -1f).let { runtime.handleMotionEvent(it, profile, backend); it.recycle() }
                runtime.awaitIdle()
                assertEquals((20..26).map { "down" to it }.toSet(), backend.calls.toSet())
                controls.forEach { (_, code, scan) -> runtime.handleKeyEvent(key(device, code, scan, KeyEvent.ACTION_UP), profile, backend) }
                motion(device, 0f, 0f).let { runtime.handleMotionEvent(it, profile, backend); it.recycle() }
                runtime.awaitIdle()
                assertEquals((20..26).map { "up" to it }.toSet(), backend.calls.filter { it.first == "up" }.toSet())
            } finally { runtime.shutdown(backend) }
        }
    }

    @Test fun stadiaExtraButtonAliasesAreDeviceSpecificAndRespectLearnedScanIdentity() {
        val other = device(false)
        assertTrue(ControllerBindingAliases.forEvent(key(other, 0, 704)).isEmpty())
        assertTrue(ControllerBindingAliases.forEvent(key(other, KeyEvent.KEYCODE_BUTTON_1, 704)).isEmpty())
        val stadia = device()
        assertTrue("ASSISTANT" in ControllerBindingAliases.forEvent(key(stadia, KeyEvent.KEYCODE_BUTTON_1, 704)))
        assertTrue("CAPTURE" in ControllerBindingAliases.forEvent(key(stadia, KeyEvent.KEYCODE_BUTTON_2, 705)))
        val learned = config(listOf(MappingNode("extra", .3f, .4f, boundKey = "ASSISTANT",
            inputKeyCode = KeyEvent.KEYCODE_BUTTON_1, inputScanCode = 704, buttonBehavior = ButtonBehavior.HOLD)))
        val backend = Recording(PrivilegeMethod.SHIZUKU)
        val runtime = GamepadMappingRuntime({ 1604 to 720 }) { fail(it) }
        try {
            assertTrue(runtime.handleKeyEvent(key(stadia, 0, 704), learned, backend))
            runtime.awaitIdle(); assertEquals(listOf("down" to 0), backend.calls)
        } finally { runtime.shutdown(backend) }
    }

    @Test fun hatLearningRetainsQuickTapThroughNeutralAndIgnoresAmbiguousDiagonal() {
        val device = device()
        motion(device, -1f, 0f).let { ControllerInputMonitor.onMotionEvent(it); it.recycle() }
        val learned = ControllerInputMonitor.state.value.lastHatPress
        assertEquals("DPAD_LEFT", learned?.binding)
        motion(device, 0f, 0f).let { ControllerInputMonitor.onMotionEvent(it); it.recycle() }
        assertEquals(learned, ControllerInputMonitor.state.value.lastHatPress)
        motion(device, 1f, 1f).let { ControllerInputMonitor.onMotionEvent(it); it.recycle() }
        assertEquals(learned, ControllerInputMonitor.state.value.lastHatPress)
    }

    @Test fun batchedHatPressAndNeutralMustNotLoseTheMappedTouch() {
        val device = device()
        val event = motion(device, -1f, 0f)
        event.addBatch(140, arrayOf(coords(0f, 0f)), 0)
        val profile = config(listOf(MappingNode("left", .2f, .3f, boundKey = "DPAD_LEFT", buttonBehavior = ButtonBehavior.HOLD)))
        val backend = Recording(PrivilegeMethod.KERNELSU)
        val runtime = GamepadMappingRuntime({ 1604 to 720 }) { fail(it) }
        try {
            assertEquals(1, event.historySize)
            ControllerInputMonitor.onMotionEvent(event)
            assertEquals("DPAD_LEFT", ControllerInputMonitor.state.value.lastHatPress?.binding)
            runtime.handleMotionEvent(event, profile, backend)
            event.recycle(); runtime.awaitIdle()
            assertEquals(listOf("down" to 0, "up" to 0), backend.calls)
        } finally { runtime.shutdown(backend) }
    }

    @Test fun diagnosticsRetainMissingKeyAndHatTargetsAfterTheRuntimeStops() {
        val device = device()
        val profile = config(listOf(MappingNode("a", .3f, .4f, boundKey = "A")))
        val backend = Recording(PrivilegeMethod.SHIZUKU)
        val runtime = GamepadMappingRuntime({ 1604 to 720 }) { fail(it) }
        ControllerRoutingTrace.clear()
        try {
            assertFalse(runtime.handleKeyEvent(key(device, 0, 704), profile, backend))
            motion(device, -1f, 0f).let { runtime.handleMotionEvent(it, profile, backend); it.recycle() }
            runtime.awaitIdle()
            runtime.shutdown(backend)
            val routes = ControllerRoutingTrace.snapshot()
            assertEquals(2, routes.size)
            assertTrue(routes.all { it.result.startsWith("unmapped:") && it.profileId == profile.id && it.backend == "SHIZUKU" })
            assertTrue(routes.first().input.contains("scan=704"))
            assertEquals("hat_left down", routes.last().input)
        } finally { runtime.shutdown(null); ControllerRoutingTrace.clear() }
    }
}
