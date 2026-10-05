package com.example

import com.example.injector.CommandResult
import com.example.module.KernelModuleStatus
import org.junit.Assert.*
import org.junit.Test

class ModuleReadinessTest {
    @Test fun installationIsOnlyReportedFromSuccessfulReadOnlyProbe() {
        assertNull(KernelModuleStatus().installed)
        assertEquals(true,KernelModuleStatus.fromProbe(CommandResult(0,"","",false)).installed)
        assertEquals(false,KernelModuleStatus.fromProbe(CommandResult(4,"","",false)).installed)
        val blocked=KernelModuleStatus.fromProbe(CommandResult(1,"","Root permission denied",false))
        assertNull(blocked.installed);assertTrue(blocked.lastActionLog.contains("permission denied"))
        assertNull(KernelModuleStatus.fromProbe(CommandResult(0,"","",true)).installed)
    }
}
