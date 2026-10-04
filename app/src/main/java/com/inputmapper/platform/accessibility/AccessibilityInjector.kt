package com.inputmapper.platform.accessibility

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import com.inputmapper.platform.core.InjectionErrorCode
import com.inputmapper.platform.core.InjectionResult
import com.inputmapper.platform.core.InputInjector
import com.inputmapper.platform.core.TimedTouchPoint
import com.inputmapper.platform.core.validateKeyAction
import com.inputmapper.platform.core.validateTap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class AccessibilityInjector(
    private val serviceProvider: () -> MapperAccessibilityService? = { MapperAccessibilityService.current }
) : InputInjector {
    override val backendName: String = "Accessibility"

    override fun injectTap(x: Float, y: Float): InjectionResult {
        validateTap(x, y)?.let { return it }
        val path = Path().apply { moveTo(x, y) }
        return dispatch(GestureDescription.StrokeDescription(path, 0, 1))
    }

    override fun injectDrag(path: List<TimedTouchPoint>, durationMillis: Long): InjectionResult {
        if (path.size < 2 || durationMillis <= 0) {
            return InjectionResult.Failure(
                InjectionErrorCode.INVALID_ARGUMENT,
                "Drag requires at least two points and durationMillis > 0"
            )
        }
        path.forEach { validateTap(it.x, it.y)?.let { failure -> return failure } }
        val gesturePath = Path().apply {
            moveTo(path.first().x, path.first().y)
            path.drop(1).forEach { lineTo(it.x, it.y) }
        }
        return dispatch(GestureDescription.StrokeDescription(gesturePath, 0, durationMillis))
    }

    override fun injectKeyEvent(keyCode: Int, action: Int): InjectionResult {
        validateKeyAction(action)?.let { return it }
        return InjectionResult.Failure(
            InjectionErrorCode.UNSUPPORTED,
            "AccessibilityService.dispatchGesture cannot inject arbitrary KeyEvent keyCode=$keyCode"
        )
    }

    private fun dispatch(stroke: GestureDescription.StrokeDescription): InjectionResult {
        val service = serviceProvider() ?: return InjectionResult.Failure(
            InjectionErrorCode.NOT_READY,
            "Accessibility service is not connected"
        )
        val latch = CountDownLatch(1)
        var completed = false
        var cancelled = false
        val accepted = try {
            service.dispatchGesture(
                GestureDescription.Builder().addStroke(stroke).build(),
                object : AccessibilityService.GestureResultCallback() {
                    override fun onCompleted(gestureDescription: GestureDescription?) {
                        completed = true
                        latch.countDown()
                    }
                    override fun onCancelled(gestureDescription: GestureDescription?) {
                        cancelled = true
                        latch.countDown()
                    }
                },
                null
            )
        } catch (t: Throwable) {
            return InjectionResult.Failure(InjectionErrorCode.REMOTE_FAILURE, "dispatchGesture threw: ${t.message}", t)
        }
        if (!accepted) {
            return InjectionResult.Failure(InjectionErrorCode.REMOTE_FAILURE, "dispatchGesture returned false")
        }
        if (!latch.await(3, TimeUnit.SECONDS)) {
            return InjectionResult.Failure(InjectionErrorCode.REMOTE_FAILURE, "Accessibility gesture callback timed out")
        }
        return if (completed && !cancelled) InjectionResult.Success else InjectionResult.Failure(
            InjectionErrorCode.REMOTE_FAILURE,
            "Accessibility gesture was cancelled"
        )
    }

    override fun cleanup(): InjectionResult = InjectionResult.Success
}
