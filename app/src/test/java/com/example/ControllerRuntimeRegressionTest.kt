package com.example

import android.graphics.PointF
import android.view.InputDevice
import com.example.injector.InputInjector
import com.example.input.ControllerSourceClassifier
import com.example.input.GamepadMappingRuntime
import com.example.model.MappingConfig
import com.example.model.MappingNode
import com.example.model.NodeType
import com.example.model.PrivilegeMethod
import java.util.concurrent.CopyOnWriteArrayList
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Regression coverage for the real-device controller capture/performance fixes. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ControllerRuntimeRegressionTest {
    private class Recording : InputInjector {
        override val method = PrivilegeMethod.KERNELSU
        val calls = CopyOnWriteArrayList<String>()
        override fun isAvailable() = true
        override fun injectTap(x: Float, y: Float) = false
        override fun injectDrag(path: List<PointF>, durationMs: Long) = false
        override fun injectKeyEvent(keyCode: Int, action: Int) = false
        override fun beginTouch(pointerId: Int, x: Float, y: Float): Boolean {
            calls += "down:$pointerId"
            return true
        }
        override fun moveTouch(pointerId: Int, x: Float, y: Float): Boolean {
            calls += "move:$pointerId"
            return true
        }
        override fun endTouch(pointerId: Int): Boolean {
            calls += "up:$pointerId"
            return true
        }
        override fun cleanup() = Unit
    }

    private fun axis(raw: Float) = GamepadMappingRuntime.AxisValue(raw, -1f, 1f, 0f)

    private fun sample(
        lx: Float = 0f,
        ly: Float = 0f,
        rx: Float = 0f,
        ry: Float = 0f
    ) = GamepadMappingRuntime.MotionSnapshot(
        leftX = axis(lx),
        leftY = axis(ly),
        rightX = axis(rx),
        rightY = axis(ry),
        leftTrigger = axis(0f),
        rightTrigger = axis(0f),
        hatX = axis(0f),
        hatY = axis(0f)
    )

    private fun profile(node: MappingNode) = MappingConfig(
        id = "test",
        profileName = "Test",
        gamePackage = "com.test.game",
        buttons = listOf(node)
    )

    @Test
    fun keyboardishEventFromControllerDeviceIsAcceptedButOrdinaryKeyboardIsNot() {
        val controllerSources = InputDevice.SOURCE_GAMEPAD or InputDevice.SOURCE_JOYSTICK
        assertTrue(ControllerSourceClassifier.accepts(InputDevice.SOURCE_KEYBOARD, controllerSources))
        assertTrue(ControllerSourceClassifier.accepts(InputDevice.SOURCE_DPAD, InputDevice.SOURCE_KEYBOARD))
        assertTrue(!ControllerSourceClassifier.accepts(InputDevice.SOURCE_KEYBOARD, InputDevice.SOURCE_KEYBOARD))
    }

    @Test
    fun leftStickDoesNotGenerateContinuousBinderMovesWhenHeldStill() {
        val runtime = GamepadMappingRuntime({ 1000 to 500 }) { error(it) }
        val backend = Recording()
        val config = profile(
            MappingNode(
                id = "ls",
                xNorm = .2f,
                yNorm = .7f,
                radiusNorm = .12f,
                type = NodeType.JOYSTICK_ZONE,
                boundKey = "LS"
            )
        )
        try {
            runtime.handleMotionSnapshot(sample(lx = .8f), config, backend)
            runtime.awaitIdle()
            val first = backend.calls.count { it.startsWith("move:") }
            assertEquals(1, first)
            Thread.sleep(60)
            assertEquals(first, backend.calls.count { it.startsWith("move:") })
        } finally {
            runtime.shutdown(backend)
        }
    }

    @Test
    fun cameraKeepsFastLoopWhileDeflectedAndStopsAfterNeutral() {
        val runtime = GamepadMappingRuntime({ 1000 to 500 }) { error(it) }
        val backend = Recording()
        val config = profile(
            MappingNode(
                id = "rs",
                xNorm = .65f,
                yNorm = .5f,
                radiusNorm = .16f,
                type = NodeType.CAMERA_DRAG,
                boundKey = "RS"
            )
        )
        try {
            runtime.handleMotionSnapshot(sample(rx = .8f), config, backend)
            runtime.awaitIdle()
            Thread.sleep(60)
            assertTrue(backend.calls.count { it.startsWith("move:") } >= 2)

            runtime.handleMotionSnapshot(sample(), config, backend)
            runtime.awaitIdle()
            Thread.sleep(25)
            val stoppedAt = backend.calls.count { it.startsWith("move:") }
            Thread.sleep(45)
            assertEquals(stoppedAt, backend.calls.count { it.startsWith("move:") })
            assertTrue(backend.calls.any { it.startsWith("up:") })
        } finally {
            runtime.shutdown(backend)
        }
    }
}
