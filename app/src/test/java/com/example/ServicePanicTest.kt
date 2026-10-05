package com.example

import android.graphics.PointF
import com.example.injector.InputInjector
import com.example.model.PrivilegeMethod
import com.example.service.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Exercises real service queue ordering with an explicitly rejecting transport fixture. */
@RunWith(RobolectricTestRunner::class) @Config(sdk=[34])
class ServicePanicTest {
    @Test fun closedBackendExecutorCannotAcknowledgeEmergencyContactRelease() {
        val service=Robolectric.buildService(ControlystAccessibilityService::class.java).create().get()
        val executor=service.javaClass.getDeclaredField("backendExecutor").apply { isAccessible=true }
            .get(service) as java.util.concurrent.ExecutorService
        executor.shutdownNow()
        var released: Boolean?=null
        try {
            service.emergencyRelease { released=it }
            assertEquals(false,released)
            assertTrue(MappingRuntimeBridge.state.value.error!!.contains("cannot be confirmed"))
        } finally { service.onDestroy();MappingRuntimeBridge.disarm() }
    }
    private class RejectCleanup : InputInjector {
        override val method=PrivilegeMethod.KERNELSU
        @Volatile var cleanups=0
        override fun isAvailable()=true
        override fun injectTap(x:Float,y:Float)=false
        override fun injectDrag(path:List<PointF>,durationMs:Long)=false
        override fun injectKeyEvent(keyCode:Int,action:Int)=false
        override fun cleanup() { cleanups++;error("fixture cleanup rejected") }
    }
    @Test fun panicCannotReportSuccessWhenAnEarlierTeardownFailedAfterClearingTheActiveField() {
        val service=Robolectric.buildService(ControlystAccessibilityService::class.java).create().get()
        val backend=RejectCleanup()
        service.javaClass.getDeclaredField("activeInjector").apply { isAccessible=true }.set(service,backend)
        service.javaClass.getDeclaredMethod("teardownInjectorAsync").apply { isAccessible=true }.invoke(service)
        val completed=CountDownLatch(1);var released=true
        service.emergencyRelease { released=it;completed.countDown() }
        try {
            assertTrue(completed.await(3,TimeUnit.SECONDS));assertFalse(released)
            assertTrue(backend.cleanups>=2);assertFalse(MappingRuntimeBridge.state.value.armed)
            assertTrue(MappingRuntimeBridge.state.value.error!!.contains("could not be confirmed"))
        } finally { service.onDestroy();MappingRuntimeBridge.disarm() }
    }
}
