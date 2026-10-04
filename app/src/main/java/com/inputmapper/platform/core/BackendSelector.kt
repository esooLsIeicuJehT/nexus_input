package com.inputmapper.platform.core

object BackendSelector {
    private val priority = listOf(
        BackendKind.KERNEL_SU,
        BackendKind.SHIZUKU,
        BackendKind.MAGISK,
        BackendKind.APATCH,
        BackendKind.ACCESSIBILITY
    )

    fun choose(
        results: List<BackendAvailability>,
        forced: BackendKind? = null
    ): BackendAvailability? {
        if (forced != null) {
            return results.firstOrNull { it.kind == forced && it.state == AvailabilityState.AVAILABLE }
        }
        return priority.asSequence()
            .mapNotNull { kind -> results.firstOrNull { it.kind == kind } }
            .firstOrNull { it.state == AvailabilityState.AVAILABLE }
    }
}
