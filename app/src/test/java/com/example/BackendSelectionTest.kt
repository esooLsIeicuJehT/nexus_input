package com.example
import com.example.injector.*
import com.example.model.PrivilegeMethod
import org.junit.Assert.*
import org.junit.Test
class BackendSelectionTest {
    private fun result(method:PrivilegeMethod,state:PrivilegeAvailabilityState)=PrivilegeProbeResult(method,state,state.name)
    @Test fun kernelSuWinsAndBlockedPermissionNeverLooksAvailable() {
        val list=listOf(result(PrivilegeMethod.SHIZUKU,PrivilegeAvailabilityState.PERMISSION_REQUIRED),
            result(PrivilegeMethod.KERNELSU,PrivilegeAvailabilityState.AVAILABLE))
        assertEquals(PrivilegeMethod.KERNELSU,PrivilegeBackendSelector.choose(list)?.method)
        assertNull(PrivilegeBackendSelector.choose(list,PrivilegeMethod.SHIZUKU))
        assertNull(PrivilegeBackendSelector.choose(emptyList()))
    }
    @Test fun apatchAlwaysFailsClosedEvenIfErroneouslyReportedAvailable() {
        val list=listOf(result(PrivilegeMethod.APATCH,PrivilegeAvailabilityState.AVAILABLE))
        assertNull(PrivilegeBackendSelector.choose(list));assertNull(PrivilegeBackendSelector.choose(list,PrivilegeMethod.APATCH))
        assertFalse(APatchInjector().isAvailable())
    }
    @Test fun forcedBackendNeverSwitchesToAnother() {
        val list=listOf(result(PrivilegeMethod.MAGISK,PrivilegeAvailabilityState.AVAILABLE),
            result(PrivilegeMethod.SHIZUKU,PrivilegeAvailabilityState.ERROR))
        assertNull(PrivilegeBackendSelector.choose(list,PrivilegeMethod.SHIZUKU))
        assertEquals(PrivilegeMethod.MAGISK,PrivilegeBackendSelector.choose(list)?.method)
    }
}
