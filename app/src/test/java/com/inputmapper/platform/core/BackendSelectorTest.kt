package com.inputmapper.platform.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BackendSelectorTest {
    @Test
    fun shizukuWinsWhenAvailable() {
        val selected = BackendSelector.choose(
            listOf(
                BackendAvailability(BackendKind.KERNEL_SU, AvailabilityState.AVAILABLE, details = "ksu"),
                BackendAvailability(BackendKind.SHIZUKU, AvailabilityState.AVAILABLE, details = "shizuku")
            )
        )
        assertEquals(BackendKind.SHIZUKU, selected?.kind)
    }

    @Test
    fun kernelSuWinsWhenShizukuNeedsPermission() {
        val selected = BackendSelector.choose(
            listOf(
                BackendAvailability(BackendKind.SHIZUKU, AvailabilityState.PERMISSION_REQUIRED, details = "permission"),
                BackendAvailability(BackendKind.KERNEL_SU, AvailabilityState.AVAILABLE, details = "ksu")
            )
        )
        assertEquals(BackendKind.KERNEL_SU, selected?.kind)
    }

    @Test
    fun forcedBackendDoesNotSilentlyFallback() {
        val selected = BackendSelector.choose(
            listOf(
                BackendAvailability(BackendKind.SHIZUKU, AvailabilityState.AVAILABLE, details = "shizuku"),
                BackendAvailability(BackendKind.KERNEL_SU, AvailabilityState.DENIED, details = "denied")
            ),
            forced = BackendKind.KERNEL_SU
        )
        assertNull(selected)
    }
}
