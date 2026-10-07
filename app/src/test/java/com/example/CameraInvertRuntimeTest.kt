package com.example

import android.graphics.PointF
import com.example.injector.InputInjector
import com.example.input.GamepadMappingRuntime
import com.example.model.CameraSettings
import com.example.model.MappingConfig
import com.example.model.MappingNode
import com.example.model.NodeType
import com.example.model.PrivilegeMethod
import java.util.concurrent.CopyOnWriteArrayList
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Verifies the persisted camera invert flag changes real runtime touch direction. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CameraInvertRuntimeTest {
    private class RecordingInjector : InputInjector {
        override val method = PrivilegeMethod.KERNELSU
        val moves = CopyOnWriteArrayList<PointF>()
        override fun isAvailable() = true
        override fun injectTap(x: Float, y: Float) = false
        override fun injectDrag(path: List<PointF>, durationMs: Long) = false
        override fun injectKeyEvent(keyCode: Int, action: Int) = false
        override fun beginTouch(pointerId: Int, x: Float, y: Float) = true
        override fun moveTouch(pointerId: Int, x: Float, y: Float): Boolean {
            moves += PointF(x, y)
            return true
        }
        override fun endTouch(pointerId: Int) = true
        override fun cleanup() = Unit
    }

    private fun axis(value: Float) = GamepadMappingRuntime.AxisValue(value, -1f, 1f, 0f)

    private fun firstCameraMove(invertY: Boolean): PointF {
        val runtime = GamepadMappingRuntime({ 1000 to 500 }) { error(it) }
        val injector = RecordingInjector()
        val config = MappingConfig(
            id = "camera-invert-test",
            profileName = "Camera invert test",
            gamePackage = "com.test.game",
            camera = CameraSettings(
                horizontalSensitivity = 1f,
                verticalSensitivity = 1f,
                smoothingFrames = 1,
                verticalRatio = 1f,
                fastTurnBoost = 1f,
                invertY = invertY
            ),
            buttons = listOf(
                MappingNode(
                    id = "rs",
                    xNorm = .5f,
                    yNorm = .5f,
                    radiusNorm = .2f,
                    type = NodeType.CAMERA_DRAG,
                    boundKey = "RS",
                    touchSlot = 9
                )
            )
        )
        val snapshot = GamepadMappingRuntime.MotionSnapshot(
            lx = axis(0f),
            ly = axis(0f),
            rx = axis(0f),
            ry = axis(.8f),
            lt = GamepadMappingRuntime.AxisValue(0f, 0f, 1f, 0f),
            rt = GamepadMappingRuntime.AxisValue(0f, 0f, 1f, 0f),
            hatX = axis(0f),
            hatY = axis(0f)
        )
        try {
            runtime.handleMotionSnapshot(snapshot, config, injector)
            val deadline = System.nanoTime() + 1_000_000_000L
            while (injector.moves.isEmpty() && System.nanoTime() < deadline) Thread.sleep(5)
            assertTrue("Expected an RS camera move", injector.moves.isNotEmpty())
            return injector.moves.first()
        } finally {
            runtime.shutdown(injector)
        }
    }

    @Test
    fun cameraInvertYFlipsVerticalTouchDirection() {
        val normal = firstCameraMove(invertY = false)
        val inverted = firstCameraMove(invertY = true)
        val anchorY = .5f * 499f
        assertTrue("Normal and inverted camera motion must land on opposite sides of the anchor",
            (normal.y - anchorY) * (inverted.y - anchorY) < 0f)
    }
}
