package com.example
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.calibration.CalibrationManager
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
@RunWith(RobolectricTestRunner::class) @Config(sdk=[34])
class CalibrationTest {
    private fun manager() = CalibrationManager(ApplicationProvider.getApplicationContext<Context>())
    @Test fun measuredValuesDetermineDeadzones() {
        val result = manager().computeDeadzones(List(5) { .03f }, List(8) { .87f })
        assertEquals(.05f, result.first, .0001f); assertEquals(.87f, result.second, .0001f)
        assertFalse(manager().stickState.value.isMeasured)
    }
    @Test fun insufficientOrInvalidTravelNeverSucceeds() {
        for ((rest, max) in listOf(emptyList<Float>() to emptyList(), List(5){.8f} to List(5){1f},
            List(5){.01f} to List(5){.05f}, List(5){Float.NaN} to List(5){1f})) {
            assertTrue(runCatching { manager().computeDeadzones(rest, max) }.isFailure)
        }
    }
    @Test fun noDeviceDoesNotCompleteCalibration() = kotlinx.coroutines.runBlocking {
        val manager=manager()
        assertFalse(manager.runStickCalibration {})
        assertFalse(manager.stickState.value.isMeasured)
        assertNotNull(manager.stickState.value.error)
    }    @Test fun measuredTriggerTravelSetsHysteresisAndRejectsUnmeasuredInput() {
        val thresholds=manager().computeTriggerThresholds(listOf(.04f,.05f,.03f),listOf(.8f,.9f,.85f))
        assertEquals(.5175f,thresholds.first,.0001f);assertEquals(.3475f,thresholds.second,.0001f)
        for((rest,pull) in listOf(emptyList<Float>() to emptyList(),listOf(0f,0f,Float.NaN) to listOf(1f,1f,1f),
            listOf(.8f,.8f,.8f) to listOf(.9f,.9f,.9f))) {
            assertTrue(runCatching { manager().computeTriggerThresholds(rest,pull) }.isFailure)
        }
    }

    private class TimingTransport : com.example.injector.InputInjector {
        override val method = com.example.model.PrivilegeMethod.KERNELSU
        var prepared = 0;var cleaned = 0;var reject = false;var cleanupFails = false
        override fun isAvailable() = true
        override fun prepare(): Boolean { prepared++;return true }
        override fun injectTap(x: Float,y: Float) = !reject
        override fun injectDrag(path: List<android.graphics.PointF>,durationMs: Long) = false
        override fun injectKeyEvent(keyCode: Int,action: Int) = false
        override fun cleanup() { cleaned++;check(!cleanupFails) { "cleanup rejected" } }
    }
    @Test fun timingAlwaysCleansItsOwnBackendAndReportsRejectedRequests() = kotlinx.coroutines.runBlocking {
        com.example.service.MappingRuntimeBridge.disarm()
        val transport = TimingTransport();val manager = manager()
        assertTrue(manager.measureTouchLatency(transport).grade.startsWith("BACKEND CALL"))
        assertEquals(1,transport.cleaned)
        transport.reject = true
        assertTrue(manager.measureTouchLatency(transport).grade.contains("Backend rejected"));assertEquals(2,transport.cleaned)
        transport.cleanupFails = true
        assertTrue(manager.measureTouchLatency(transport).grade.contains("backend cleanup"));assertEquals(3,transport.cleaned)
    }
    @Test fun timingDoesNotPrepareOrCloseAnArmedBackend() = kotlinx.coroutines.runBlocking {
        val transport=TimingTransport()
        com.example.service.MappingRuntimeBridge.arm("com.test.game",com.example.model.MappingConfig(id="timing",profileName="Timing",gamePackage="com.test.game",buttons=listOf(com.example.model.MappingNode("a",.2f,.3f))))
        try {
            assertTrue(manager().measureTouchLatency(transport).grade.contains("Stop mapping"))
            assertEquals(0,transport.prepared);assertEquals(0,transport.cleaned)
        } finally { com.example.service.MappingRuntimeBridge.disarm() }
    }

}
