package com.example.injector

import com.example.model.PrivilegeMethod

/**
 * Automatic backend priority carried over from the device-verified 0.6.2 build.
 * A backend must be AVAILABLE. A requestable/blocked Shizuku binder never outranks
 * a working KernelSU backend.
 */
object PrivilegeBackendSelector {
    private val priority = listOf(
        PrivilegeMethod.KERNELSU,
        PrivilegeMethod.SHIZUKU,
        PrivilegeMethod.MAGISK,
        PrivilegeMethod.ACCESSIBILITY
    )

    fun choose(
        results: List<PrivilegeProbeResult>,
        forced: PrivilegeMethod? = null
    ): PrivilegeProbeResult? {
        if (forced == PrivilegeMethod.APATCH) return null
        if (forced != null) {
            return results.firstOrNull {
                it.method == forced && it.state == PrivilegeAvailabilityState.AVAILABLE
            }
        }

        return priority.asSequence()
            .mapNotNull { method -> results.firstOrNull { it.method == method } }
            .firstOrNull { it.state == PrivilegeAvailabilityState.AVAILABLE }
    }
}
