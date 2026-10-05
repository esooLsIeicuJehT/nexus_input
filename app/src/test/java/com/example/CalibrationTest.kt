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
    }
}
