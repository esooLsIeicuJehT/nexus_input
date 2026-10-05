package com.example
import androidx.lifecycle.Lifecycle
import com.example.service.OverlayOwner
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
@RunWith(RobolectricTestRunner::class) @Config(sdk=[34])
class OverlayOwnerTest {
    @Test fun onlyTheMappingServiceHostsConfiguredCrosshairWindows() {
        val context=androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        @Suppress("DEPRECATION")
        val services=context.packageManager.getPackageInfo(context.packageName,android.content.pm.PackageManager.GET_SERVICES).services
        assertTrue(services.any { it.name.endsWith(".MappingForegroundService") })
        assertFalse(services.any { it.name.endsWith(".CrosshairOverlayService") })
    }
    @Test fun composeWindowHasRestoredSavedStateAndACompleteLifecycle() {
        val owner=OverlayOwner()
        assertTrue(owner.savedStateRegistry.isRestored)
        assertEquals(Lifecycle.State.RESUMED,owner.lifecycle.currentState)
        owner.destroy()
        assertEquals(Lifecycle.State.DESTROYED,owner.lifecycle.currentState)
    }
    @Test fun crosshairSpreadRequiresObservedStickAxesAndGeometryFitsOffsets() {
        val config=com.example.model.CrosshairConfig()
        assertEquals(1f,com.example.model.CrosshairGeometry.observedSpread(config,emptyMap()),0f)
        assertEquals(1f,com.example.model.CrosshairGeometry.observedSpread(config,mapOf("LX" to 1f)),0f)
        assertEquals(1.5f,com.example.model.CrosshairGeometry.observedSpread(config,mapOf("LX" to .3f,"LY" to .4f)),.0001f)
        assertEquals(1f,com.example.model.CrosshairGeometry.observedSpread(config.copy(dynamicSpread=false),mapOf("LX" to 1f,"LY" to 1f)),0f)
        assertEquals(2f,com.example.model.CrosshairGeometry.observedSpread(config,mapOf("RX" to 1f,"RY" to 1f)),0f)
        assertTrue(com.example.model.CrosshairGeometry.extentDp(config.copy(offsetX=50f)) > com.example.model.CrosshairGeometry.extentDp(config))
        assertTrue(runCatching { com.example.ui.crosshair.parseHexColor("invalid") }.isFailure)
        assertEquals(androidx.compose.ui.graphics.Color.White,com.example.ui.crosshair.parseHexColor("#FFFFFF"))
    }

}
