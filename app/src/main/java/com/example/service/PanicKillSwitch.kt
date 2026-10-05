package com.example.service

import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.injector.InputInjector
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

data class PanicState(
    val isKilled: Boolean = false,
    val lastTriggerTime: Long = 0L,
    val triggerSource: String = "Idle",
    val activeMacrosStoppedCount: Int = 0,
    val heldVirtualButtonsReleased: Int = 0,
    val releaseConfirmed: Boolean = false,
    val error: String? = null
)

object PanicKillSwitch {
    private val _state = MutableStateFlow(PanicState())
    val state = _state.asStateFlow()
    private var onPanicTriggeredListener: (() -> Unit)? = null
    fun setPanicListener(listener: () -> Unit) { onPanicTriggeredListener = listener }
    @Suppress("UNUSED_PARAMETER")
    fun trigger(context: Context, injector: InputInjector? = null): Int {
        triggerPanic(context)
        // A synchronous UI call cannot know an asynchronous release count.
        return 0
    }
    fun triggerPanic(context: Context, source: String = "Manual UI Button") {
        _state.value = PanicState(true, System.currentTimeMillis(), source)
        val service = ControlystAccessibilityService.getInstance()
        if (service != null) service.emergencyRelease { released ->
            _state.value = _state.value.copy(releaseConfirmed = released,
                error = if (released) null else "Contact release or backend cleanup failed; inspect Android logs and backend state.")
            Log.w("NexusPanic", "Emergency release completed; backend acknowledgement=$released")
        } else {
            MappingRuntimeBridge.disarm("Emergency stop requested")
            _state.value = _state.value.copy(error = "Capture service unavailable; release could not be confirmed.")
            Log.e("NexusPanic", "Cannot confirm release without capture service")
        }
        try {
            context.startService(Intent(context, MappingForegroundService::class.java).apply {
                action = MappingForegroundService.ACTION_STOP_MAPPING
            })
        } catch (error: Exception) {
            _state.value = _state.value.copy(error = "Foreground service stop failed: ${error.message}")
            Log.e("NexusPanic", "Foreground stop failed", error)
        }
        runCatching { onPanicTriggeredListener?.invoke() }.onFailure { Log.e("NexusPanic", "Overlay close failed", it) }
    }
    fun resetPanic() { _state.value = PanicState() }
}
