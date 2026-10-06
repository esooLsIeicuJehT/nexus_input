package com.example.service

import com.example.model.MappingConfig
import com.example.model.PrivilegeMethod
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class MappingRuntimeState(
    val armed: Boolean = false,
    val targetForeground: Boolean = false,
    val gamePackage: String? = null,
    val configId: String? = null,
    val profileName: String? = null,
    val backend: PrivilegeMethod? = null,
    val backendReady: Boolean = false,
    val error: String? = null,
    val notice: String? = null,
    val sessionId: Long = 0L
)

/**
 * Process-local handoff between the foreground lifecycle service and the
 * AccessibilityService that receives controller events while a game has focus.
 *
 * No defaults are invented here. A mapping is armed only with an exact loaded
 * MappingConfig, and backend failures remain visible in [state].
 */
object MappingRuntimeBridge {
    private val sessions = java.util.concurrent.atomic.AtomicLong()
    private val _state = MutableStateFlow(MappingRuntimeState())
    val state: StateFlow<MappingRuntimeState> = _state.asStateFlow()

    private val _config = MutableStateFlow<MappingConfig?>(null)
    val config: StateFlow<MappingConfig?> = _config.asStateFlow()

    fun arm(gamePackage: String, config: MappingConfig): Boolean {
        val errors=com.example.input.ProfileValidator.runtimeErrors(config,gamePackage,config.id)
        if(errors.isNotEmpty()) { disarm("Mapping rejected: ${errors.joinToString("; ")}");return false }
        _config.value = config
        _state.value = MappingRuntimeState(
            armed = true,
            targetForeground = false,
            gamePackage = gamePackage,
            configId = config.id,
            profileName = config.profileName,
            sessionId = sessions.incrementAndGet()
        )
        return true
    }

    fun setForegroundPackage(packageName: String?) {
        val current = _state.value
        if (!current.armed) return
        val isTarget = packageName != null && packageName == current.gamePackage
        _state.value = current.copy(
            targetForeground = isTarget,
            error = current.error
        )
    }

    fun setBackend(method: PrivilegeMethod, ready: Boolean, error: String? = null, notice: String? = null) {
        val current = _state.value
        _state.value = current.copy(
            backend = method,
            backendReady = ready,
            error = error,
            notice = notice
        )
    }

    fun clearBackendReady() {
        _state.value = _state.value.copy(backendReady = false)
    }

    fun reportError(message: String) {
        _state.value = _state.value.copy(error = message, backendReady = false)
    }

    fun disarm(error: String? = null) {
        _config.value = null
        _state.value = MappingRuntimeState(error = error)
    }
}
