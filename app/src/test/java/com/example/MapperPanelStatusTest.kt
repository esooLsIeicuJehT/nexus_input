package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.model.PrivilegeMethod
import com.example.service.InGameMapperOverlay
import com.example.service.MapperPanelStatus
import com.example.service.MappingRuntimeBridge
import com.example.service.MappingRuntimeState
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** UI state tests only; no game window or injected input is claimed. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MapperPanelStatusTest {
    @Test fun panelShowsTheObservedConnectionAndFailureWithoutClaimingGameDelivery() {
        val state = MappingRuntimeState(armed=true, targetForeground=false, profileName="Local test profile",
            backend=PrivilegeMethod.SHIZUKU, backendReady=false,
            notice="Fixture bridge initialized; game delivery unverified", error="Fixture dispatcher rejected DOWN")
        val text = MapperPanelStatus.text(state)
        assertTrue(text.contains("Local test profile"))
        assertTrue(text.contains("Backend connection prepared: false"))
        assertTrue(text.contains("Target in foreground: false"))
        assertTrue(text.contains(state.notice!!))
        assertTrue(text.contains(state.error!!))
        assertFalse(text.contains("touch delivered"))
    }

    @Test fun openingEditAfterDisarmPreservesTheOriginalInjectionFailure() {
        MappingRuntimeBridge.disarm("Original fixture injection failure")
        val overlay = InGameMapperOverlay(ApplicationProvider.getApplicationContext<Context>())
        try {
            overlay.javaClass.getDeclaredMethod("beginEdit").apply { isAccessible=true }.invoke(overlay)
            assertEquals("Original fixture injection failure", MappingRuntimeBridge.state.value.error)
            assertFalse(MappingRuntimeBridge.state.value.armed)
            assertTrue(MapperPanelStatus.text(MappingRuntimeBridge.state.value).contains("Original fixture injection failure"))
        } finally { overlay.hide();MappingRuntimeBridge.disarm() }
    }
}
