package com.example
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.model.*
import com.example.service.PanicKillSwitch
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
@RunWith(RobolectricTestRunner::class) @Config(sdk=[34])
class IntegrityAuditTest {
    @Test fun noInventedStatisticsOrEntitlementsOnNewProfile() {
        val config=MappingConfig(id="test",profileName="Test",gamePackage="com.test.game")
        assertEquals(0f,config.rating,0f);assertEquals(0,config.downloadCount);assertFalse(config.isOfficialVerified)
    }
    @Test fun panicWithoutServiceNeverInventsReleasedInputCounts() {
        PanicKillSwitch.resetPanic()
        PanicKillSwitch.trigger(ApplicationProvider.getApplicationContext<Context>())
        val state=PanicKillSwitch.state.value
        assertFalse(state.releaseConfirmed);assertNotNull(state.error)
        assertEquals(0,state.heldVirtualButtonsReleased);assertEquals(0,state.activeMacrosStoppedCount)
    }
}
