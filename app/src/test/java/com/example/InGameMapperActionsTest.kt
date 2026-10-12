package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.input.MapperEditSession
import com.example.model.*
import com.example.service.InGameMapperOverlay
import com.example.service.MappingRuntimeBridge
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Exercises real overlay action guards; no screenshot or privileged input is synthesized. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[34])
class InGameMapperActionsTest {
    @Test fun screenshotActionCannotReplaceAnExistingInjectionFailureOrPretendToCapture() {
        MappingRuntimeBridge.disarm("Actual-state fixture: dispatcher rejected the mapping")
        val overlay=InGameMapperOverlay(ApplicationProvider.getApplicationContext<Context>())
        try {
            overlay.javaClass.getDeclaredMethod("captureForMapper").apply { isAccessible=true }.invoke(overlay)
            assertEquals("Actual-state fixture: dispatcher rejected the mapping",MappingRuntimeBridge.state.value.error)
            assertFalse(MappingRuntimeBridge.state.value.armed)
            assertFalse(MappingRuntimeBridge.state.value.backendReady)
        } finally { overlay.hide();MappingRuntimeBridge.disarm() }
    }

    @Test fun profileSwitchRequiresAnActualActiveGameAndPreservesTheExistingFailure() {
        MappingRuntimeBridge.disarm("Actual-state fixture: backend process disconnected")
        val overlay=InGameMapperOverlay(ApplicationProvider.getApplicationContext<Context>())
        try {
            overlay.javaClass.getDeclaredMethod("chooseProfile").apply { isAccessible=true }.invoke(overlay)
            assertEquals("Actual-state fixture: backend process disconnected",MappingRuntimeBridge.state.value.error)
            assertNull(MappingRuntimeBridge.config.value)
            assertFalse(MappingRuntimeBridge.state.value.armed)
        } finally { overlay.hide();MappingRuntimeBridge.disarm() }
    }

    @Test fun macroTurboAndStickTargetsDoNotOfferAnIgnoredTapHoldDialog() {
        val context=ApplicationProvider.getApplicationContext<Context>()
        val overlay=InGameMapperOverlay(context)
        try {
            listOf(NodeType.MACRO,NodeType.TURBO,NodeType.JOYSTICK_ZONE,NodeType.CAMERA_DRAG).forEach { type ->
                val config=MappingConfig(id="game",profileName="Game",gamePackage="com.test.game",buttons=listOf(
                    MappingNode("target",.5f,.5f,type=type,boundKey=when(type) {
                        NodeType.JOYSTICK_ZONE -> "LS";NodeType.CAMERA_DRAG -> "RS";else -> "A"
                    },macroActions=if(type==NodeType.MACRO) listOf(MacroStep()) else emptyList())))
                val edit=MapperEditSession(config)
                overlay.javaClass.getDeclaredField("session").apply { isAccessible=true }.set(overlay,edit)
                overlay.javaClass.getDeclaredField("selected").apply { isAccessible=true }.set(overlay,"target")
                overlay.javaClass.getDeclaredMethod("chooseBehavior",android.view.View::class.java)
                    .apply { isAccessible=true }.invoke(overlay,android.view.View(context))
                assertNull(overlay.javaClass.getDeclaredField("dialog").apply { isAccessible=true }.get(overlay))
                assertEquals(config,edit.draft)
            }
        } finally { overlay.hide() }
    }
}
