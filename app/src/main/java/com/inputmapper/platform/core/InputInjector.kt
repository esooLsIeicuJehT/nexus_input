package com.inputmapper.platform.core

import android.view.KeyEvent

data class TouchPoint(val x: Float, val y: Float)

data class TimedTouchPoint(val x: Float, val y: Float, val atMillis: Long)

sealed interface InjectionResult {
    data object Success : InjectionResult
    data class Failure(val code: InjectionErrorCode, val message: String, val cause: Throwable? = null) : InjectionResult
}

enum class InjectionErrorCode {
    NOT_READY,
    PERMISSION_DENIED,
    BACKEND_UNAVAILABLE,
    UNSUPPORTED,
    INVALID_ARGUMENT,
    NATIVE_FAILURE,
    REMOTE_FAILURE,
    IO_FAILURE,
    CLEANUP_FAILURE
}

interface InputInjector : AutoCloseable {
    val backendName: String

    fun injectTap(x: Float, y: Float): InjectionResult

    fun injectDrag(path: List<TimedTouchPoint>, durationMillis: Long): InjectionResult

    fun injectKeyEvent(keyCode: Int, action: Int): InjectionResult

    fun beginTouch(pointerId: Int, x: Float, y: Float): InjectionResult = InjectionResult.Failure(
        InjectionErrorCode.UNSUPPORTED,
        "$backendName does not support persistent touch contacts"
    )

    fun moveTouch(pointerId: Int, x: Float, y: Float): InjectionResult = InjectionResult.Failure(
        InjectionErrorCode.UNSUPPORTED,
        "$backendName does not support persistent touch contacts"
    )

    fun endTouch(pointerId: Int): InjectionResult = InjectionResult.Failure(
        InjectionErrorCode.UNSUPPORTED,
        "$backendName does not support persistent touch contacts"
    )

    fun publishRuntimeState(state: String): InjectionResult = InjectionResult.Failure(
        InjectionErrorCode.UNSUPPORTED,
        "$backendName does not expose a root-companion state channel"
    )

    fun cleanup(): InjectionResult

    override fun close() {
        cleanup()
    }
}

internal fun validateTap(x: Float, y: Float): InjectionResult.Failure? {
    if (!x.isFinite() || !y.isFinite()) {
        return InjectionResult.Failure(
            InjectionErrorCode.INVALID_ARGUMENT,
            "Touch coordinates must be finite: x=$x y=$y"
        )
    }
    return null
}

internal fun validateKeyAction(action: Int): InjectionResult.Failure? {
    if (action != KeyEvent.ACTION_DOWN && action != KeyEvent.ACTION_UP) {
        return InjectionResult.Failure(
            InjectionErrorCode.INVALID_ARGUMENT,
            "Unsupported key action=$action; expected ACTION_DOWN or ACTION_UP"
        )
    }
    return null
}
