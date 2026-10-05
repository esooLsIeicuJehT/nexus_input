package com.example
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.FirebaseRepository
import com.example.model.*
import com.example.module.KernelSuModuleManager
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
    @Test fun oauthRejectsMissingOrPlaceholderConfiguration() {
        assertFalse(FirebaseRepository.isValidOAuthClientId(""))
        assertFalse(FirebaseRepository.isValidOAuthClientId("393750783022-placeholder.apps.googleusercontent.com"))
        assertTrue(FirebaseRepository.isValidOAuthClientId("123456-realclient.apps.googleusercontent.com"))
    }
    @Test fun canonicalModuleArchiveContainsUpdaterAndDoesNotChangeDevicePermissions() = kotlinx.coroutines.runBlocking {
        val context=ApplicationProvider.getApplicationContext<Context>()
        val zip=KernelSuModuleManager.generateModuleZip(context)
        java.util.zip.ZipFile(zip).use {
            assertNotNull(it.getEntry("webroot/app.js"));assertNotNull(it.getEntry("update.sh"))
            val service=it.getInputStream(it.getEntry("service.sh")).bufferedReader().readText()
            assertFalse(service.contains("chmod 666"));assertFalse(service.contains("chmod 777"))
            assertFalse(service.contains("zero-latency"))
        }
    }
    @Test fun panicWithoutServiceNeverInventsReleasedInputCounts() {
        PanicKillSwitch.resetPanic()
        PanicKillSwitch.trigger(ApplicationProvider.getApplicationContext<Context>())
        val state=PanicKillSwitch.state.value
        assertFalse(state.releaseConfirmed);assertNotNull(state.error)
        assertEquals(0,state.heldVirtualButtonsReleased);assertEquals(0,state.activeMacrosStoppedCount)
    }
}
