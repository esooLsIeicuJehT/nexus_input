package com.example

import android.content.Context
import android.content.Intent
import android.app.Notification
import android.widget.TextView
import androidx.compose.ui.platform.ComposeView
import androidx.test.core.app.ApplicationProvider
import com.example.frames.FrameMonitor
import com.example.frames.FrameMonitorState
import com.example.service.CrosshairOverlayManager
import com.example.service.FrameTimeOverlay
import com.example.service.InGameMapperOverlay
import com.example.service.MappingRuntimeBridge
import com.example.service.MappingForegroundService
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** Unattached view fixtures make Android's real removeView fail; no WindowManager success is mocked. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[34])
class OwnedOverlayRemovalTest {
    private val context=ApplicationProvider.getApplicationContext<Context>()

    @Test fun frameWindowRemovalFailureIsReturnedAndVisibleInsteadOfAllowingCapture() {
        val overlay=FrameTimeOverlay(context)
        val view=TextView(context)
        val field=overlay.javaClass.getDeclaredField("view").apply { isAccessible=true }
        field.set(overlay,view)
        try {
            assertTrue(overlay.hide().isFailure)
            assertSame(view,field.get(overlay))
            assertTrue(FrameMonitor.state.value.error.orEmpty().contains("removal failed"))
        } finally { field.set(overlay,null);FrameMonitor.update(FrameMonitorState()) }
    }

    @Test fun crosshairWindowRemovalFailureIsReturnedAndRetainedForCleanup() {
        val overlay=CrosshairOverlayManager(context)
        val view=ComposeView(context)
        val field=overlay.javaClass.getDeclaredField("overlayView").apply { isAccessible=true }
        field.set(overlay,view)
        try {
            assertTrue(overlay.hideOverlay().isFailure)
            assertSame(view,field.get(overlay))
            assertTrue(MappingRuntimeBridge.state.value.error.orEmpty().contains("Crosshair removal failed"))
        } finally { field.set(overlay,null);MappingRuntimeBridge.disarm() }
    }

    @Test fun mapperWindowRemovalFailureAbortsCheckedCaptureAndRemainsObservable() {
        val overlay=InGameMapperOverlay(context)
        val field=overlay.javaClass.getDeclaredField("bubble").apply { isAccessible=true }
        val view=TextView(context)
        field.set(overlay,view)
        try {
            assertTrue(overlay.hide().isFailure)
            assertSame(view,field.get(overlay))
            assertTrue(overlay.hide().isFailure)
            assertSame(view,field.get(overlay))
            assertTrue(MappingRuntimeBridge.state.value.error.orEmpty().contains("Overlay removal failed"))
        } finally { field.set(overlay,null);overlay.hide();MappingRuntimeBridge.disarm() }
    }

    @Test fun stoppingMappingDemotesTheAndroidForegroundServiceBeforeEndingOwnership() {
        val controller=Robolectric.buildService(MappingForegroundService::class.java).create()
        val service=controller.get()
        val observed=shadowOf(service)
        try {
            service.startForeground(MappingForegroundService.NOTIFICATION_ID,
                Notification.Builder(context,MappingForegroundService.CHANNEL_ID)
                    .setSmallIcon(com.example.R.drawable.ic_launcher_foreground)
                    .setContentTitle("Explicit test fixture: active foreground service").build())
            assertFalse(observed.isForegroundStopped)
            assertNotNull(observed.lastForegroundNotification)
            service.onStartCommand(Intent(context,MappingForegroundService::class.java).apply {
                action=MappingForegroundService.ACTION_STOP_MAPPING
            },0,1)
            assertTrue(observed.isForegroundStopped)
            assertNull(observed.lastForegroundNotification)
            assertTrue(observed.isStoppedBySelf)
            assertFalse(MappingForegroundService.isServiceActive.value)
            assertFalse(MappingRuntimeBridge.state.value.armed)
        } finally { controller.destroy();MappingRuntimeBridge.disarm() }
    }

    @Test fun serviceRetainsFailedOwnedWindowsForTheDestructionCleanupRetry() {
        val controller=Robolectric.buildService(MappingForegroundService::class.java).create()
        val service=controller.get()
        val owned=InGameMapperOverlay(service)
        val window=TextView(service)
        val windowField=owned.javaClass.getDeclaredField("bubble").apply { isAccessible=true }
        val ownerField=service.javaClass.getDeclaredField("inGameOverlay").apply { isAccessible=true }
        windowField.set(owned,window);ownerField.set(service,owned)
        try {
            service.onStartCommand(Intent(context,MappingForegroundService::class.java).apply {
                action=MappingForegroundService.ACTION_STOP_MAPPING
            },0,1)
            assertSame(owned,ownerField.get(service))
            assertSame(window,windowField.get(owned))
            assertTrue(MappingRuntimeBridge.state.value.error.orEmpty().contains("Overlay removal failed"))
        } finally {
            windowField.set(owned,null);controller.destroy();MappingRuntimeBridge.disarm()
        }
    }
}
