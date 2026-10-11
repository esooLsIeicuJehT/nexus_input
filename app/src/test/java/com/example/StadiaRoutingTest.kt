package com.example

import android.app.Application
import android.graphics.PointF
import android.hardware.input.InputManager
import android.hardware.input.IInputManager
import android.view.InputDevice
import android.view.InputEvent
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
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import com.inputmapper.platform.root.PrivilegedInputManagerEngine
import com.example.diagnostics.ReleaseDiagnostics
import com.example.service.ControlystAccessibilityService
import com.example.service.MappingRuntimeBridge
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Robolectric
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
            .addMotionRange(MotionEvent.AXIS_Z, InputDevice.SOURCE_JOYSTICK, -1f, 1f, 0f, 0f, 0f)
            .addMotionRange(MotionEvent.AXIS_RZ, InputDevice.SOURCE_JOYSTICK, -1f, 1f, 0f, 0f, 0f)
            .addMotionRange(MotionEvent.AXIS_LTRIGGER, InputDevice.SOURCE_JOYSTICK, 0f, 1f, 0f, 0f, 0f)
            .addMotionRange(MotionEvent.AXIS_RTRIGGER, InputDevice.SOURCE_JOYSTICK, 0f, 1f, 0f, 0f, 0f)
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

    @Test fun reportedSeventeenNodeProfileRoutesStartSelectAndAllHatsIntoRootMotionEvents() {
        val device = device()
        // The user's diagnostic order, normalized coordinates, and null physical/slot fields.
        // This exercises default TAP targets and pointer ID 16, unlike the HOLD-only fixture.
        val targets = listOf(
            Triple("A", .8938093f, .9103233f), Triple("B", .8149076f, .92005634f),
            Triple("LT", .95930177f, .49322248f), Triple("RT", .096366554f, .50712526f),
            Triple("X", .7375642f, .8950297f), Triple("Y", .85888034f, .2443525f),
            Triple("LS", .20427275f, .7865837f), Triple("RS", .75206643f, .16301666f),
            Triple("R3", .9596137f, .8567957f), Triple("LB", .6585595f, .7455681f),
            Triple("RB", .65782845f, .8992008f), Triple("DPAD_LEFT", .17464508f, .52346194f),
            Triple("DPAD_RIGHT", .23000148f, .52798074f), Triple("START", .92528903f, .054529946f),
            Triple("SELECT", .12984443f, .19026127f), Triple("DPAD_UP", .7838769f, .517445f),
            Triple("DPAD_DOWN", .8532671f, .47445318f))
        val profile = config(targets.map { (binding, x, y) ->
            MappingNode(binding, x, y, boundKey = binding, type = when (binding) {
                "LS" -> NodeType.JOYSTICK_ZONE
                "RS" -> NodeType.CAMERA_DRAG
                else -> NodeType.BUTTON
            })
        }).copy(id = "builtin_delta_force_nexus_v1")
        assertTrue(ProfileValidator.runtimeErrors(profile).toString(), ProfileValidator.runtimeErrors(profile).isEmpty())
        data class Touch(val action: Int, val id: Int, val x: Float, val y: Float)
        val injected = CopyOnWriteArrayList<Touch>()
        val released = CountDownLatch(6)
        val inputManager = object : IInputManager.Stub() {
            override fun injectInputEvent(event: InputEvent, mode: Int): Boolean {
                assertEquals(0, mode)
                val motion = event as MotionEvent
                val index = motion.actionIndex
                injected += Touch(motion.actionMasked, motion.getPointerId(index), motion.getX(index), motion.getY(index))
                if (motion.actionMasked in listOf(MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP)) released.countDown()
                return true
            }
        }
        // Replace only the OS boundary. Exercise the real root engine's sparse pointer
        // IDs, action indices and coordinate composition; no root permission is simulated.
        val engine = PrivilegedInputManagerEngine(32)
        engine.javaClass.getDeclaredField("manager").apply { isAccessible = true }.set(engine, inputManager)
        val backend = object : InputInjector {
            override val method = PrivilegeMethod.KERNELSU
            override fun isAvailable() = true
            override fun injectTap(x: Float, y: Float) = false
            override fun injectDrag(path: List<PointF>, durationMs: Long) = false
            override fun injectKeyEvent(keyCode: Int, action: Int) = false
            override fun beginTouch(pointerId: Int, x: Float, y: Float) = engine.down(pointerId, x, y).startsWith("OK")
            override fun moveTouch(pointerId: Int, x: Float, y: Float) = engine.move(pointerId, x, y).startsWith("OK")
            override fun endTouch(pointerId: Int) = engine.up(pointerId).startsWith("OK")
            override fun cleanup() = Unit
        }
        val errors = CopyOnWriteArrayList<String>()
        val runtime = GamepadMappingRuntime({ 1604 to 720 }) { errors += it }
        try {
            for ((code, scan) in listOf(108 to 315, 109 to 314)) {
                assertTrue(runtime.handleKeyEvent(key(device, code, scan), profile, backend))
                assertTrue(runtime.handleKeyEvent(key(device, code, scan, KeyEvent.ACTION_UP), profile, backend))
            }
            for ((x, y) in listOf(-1f to 0f, 1f to 0f, 0f to -1f, 0f to 1f)) {
                motion(device, x, y).let { runtime.handleMotionEvent(it, profile, backend); it.recycle() }
                motion(device, 0f, 0f).let { runtime.handleMotionEvent(it, profile, backend); it.recycle() }
            }
            runtime.awaitIdle()
            assertTrue("All six default taps must release", released.await(2, TimeUnit.SECONDS))
            assertTrue(errors.toString(), errors.isEmpty())
            val downs = injected.filter { it.action in listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN) }
            assertEquals(setOf(11, 12, 13, 14, 15, 16), downs.map { it.id }.toSet())
            for (touch in downs) {
                val node = profile.buttons[touch.id]
                assertEquals(node.xNorm * 1603, touch.x, .001f)
                assertEquals(node.yNorm * 719, touch.y, .001f)
            }
            assertEquals(6, downs.size)
        } finally { runtime.shutdown(backend) }
    }

    @Test fun compactInputReportKeepsBindingsAndRoutesWithoutTheLongMotionLog() {
        val full = JSONObject().put("runtime", JSONObject().put("armed", false))
            .put("controllerRouting", JSONObject().put("selectedProfile", "saved")
                .put("bindingsProfile", "saved").put("bindings", org.json.JSONArray().put(JSONObject().put("binding", "START")))
                .put("events", org.json.JSONArray().put("irrelevant motion"))
                .put("recentRoutes", org.json.JSONArray().apply { repeat(48) { put(JSONObject().put("input", it)) } }))
        val compact = JSONObject(ReleaseDiagnostics.inputReport(full.toString())!!)
        assertFalse(compact.getJSONObject("runtime").getBoolean("armed"))
        val routing = compact.getJSONObject("controllerRouting")
        assertFalse(routing.has("events"))
        assertEquals("START", routing.getJSONArray("bindings").getJSONObject(0).getString("binding"))
        assertEquals(24, routing.getJSONArray("recentRoutes").length())
        assertEquals(24, routing.getJSONArray("recentRoutes").getJSONObject(0).getInt("input"))
        assertEquals(47, routing.getJSONArray("recentRoutes").getJSONObject(23).getInt("input"))
        assertNull(ReleaseDiagnostics.inputReport("Self-check failed: unavailable"))
    }

    @Test fun accessibilityReportsDisarmedKeysAndBlockedBatchedHatWithoutRepeatedFrames() {
        val device = device()
        val controller = Robolectric.buildService(ControlystAccessibilityService::class.java).create()
        val service = controller.get()
        MappingRuntimeBridge.disarm()
        ControllerRoutingTrace.clear()
        try {
            val captureKey = service.javaClass.getDeclaredMethod("onKeyEvent", KeyEvent::class.java)
                .apply { isAccessible = true }
            assertFalse(captureKey.invoke(service, key(device, 108, 315)) as Boolean)
            assertEquals("blocked: mapper is disarmed", ControllerRoutingTrace.snapshot().single().result)
            val profile = config(listOf(MappingNode("left", .2f, .3f, boundKey = "DPAD_LEFT")))
            assertTrue(MappingRuntimeBridge.arm(profile.gamePackage, profile))
            val batched = motion(device, -1f, 0f)
            batched.addBatch(140, arrayOf(coords(0f, 0f)), 0)
            service.onMotionEvent(batched); batched.recycle()
            repeat(3) { motion(device, 0f, 0f).let { service.onMotionEvent(it); it.recycle() } }
            val routes = ControllerRoutingTrace.snapshot()
            assertEquals(2, routes.size)
            assertEquals("DPAD_LEFT down", routes.last().input)
            assertEquals("blocked: target game is not foreground", routes.last().result)
            assertEquals(profile.id, routes.last().profileId)
        } finally {
            controller.destroy()
            ControllerRoutingTrace.clear()
            MappingRuntimeBridge.disarm()
        }
    }
}
