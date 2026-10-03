package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.ControlystRepository
import com.example.injector.InputInjectorFactory
import com.example.injector.PrivilegeDetector
import com.example.model.ControllerType
import com.example.model.NodeType
import com.example.model.PrivilegeMethod
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

    @Test
    fun `verify app name resource is Controlyst`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("Controlyst", appName)
    }

    @Test
    fun `verify privilege detector probes accessibility fallback`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val detector = PrivilegeDetector(context)
        val probes = detector.probeAll()

        assertEquals(5, probes.size)
        assertTrue(probes.any { it.method == PrivilegeMethod.ACCESSIBILITY })
        assertTrue(probes.any { it.method == PrivilegeMethod.SHIZUKU })
        assertTrue(probes.any { it.method == PrivilegeMethod.MAGISK })
        assertTrue(probes.any { it.method == PrivilegeMethod.KERNELSU })
        assertTrue(probes.any { it.method == PrivilegeMethod.APATCH })
    }

    @Test
    fun `verify input injector factory creates injectors`() {
        val a11yInjector = InputInjectorFactory.createInjector(PrivilegeMethod.ACCESSIBILITY)
        assertNotNull(a11yInjector)

        val shizukuInjector = InputInjectorFactory.createInjector(PrivilegeMethod.SHIZUKU)
        assertNotNull(shizukuInjector)

        val magiskInjector = InputInjectorFactory.createInjector(PrivilegeMethod.MAGISK)
        assertNotNull(magiskInjector)
    }

    @Test
    fun `verify json serialization roundtrip for mapping config`() {
        val originalConfig = ControlystRepository.createSampleDfmConfig()
        val jsonStr = ControlystRepository.serializeConfigToJson(originalConfig)

        assertTrue(jsonStr.contains("schemaVersion"))
        assertTrue(jsonStr.contains("dfm_pro_ranked"))
        assertTrue(jsonStr.contains("b_fire"))

        val deserialized = ControlystRepository.deserializeJsonToConfig(jsonStr)
        assertEquals(originalConfig.id, deserialized.id)
        assertEquals(originalConfig.gamePackage, deserialized.gamePackage)
        assertEquals(originalConfig.buttons.size, deserialized.buttons.size)
        assertEquals(originalConfig.crosshair.shape, deserialized.crosshair.shape)
    }

    @Test
    fun `verify Shizuku pairing manager rejects non-6-digit code and accepts valid code`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        com.example.service.ShizukuPairingManager.showPairingNotification(context, 5555)

        assertTrue(com.example.service.ShizukuPairingManager.pairingState.value.isHelperNotificationActive)

        // Invalid code test
        com.example.service.ShizukuPairingManager.handlePairingCodeReceived(context, "123", 5555)
        assertFalse(com.example.service.ShizukuPairingManager.pairingState.value.isPairingSuccessful)

        // Valid 6-digit code test
        com.example.service.ShizukuPairingManager.handlePairingCodeReceived(context, "987654", 5555)
        assertTrue(com.example.service.ShizukuPairingManager.pairingState.value.isPairingSuccessful)
        assertEquals("987654", com.example.service.ShizukuPairingManager.pairingState.value.lastEnteredCode)
    }

    @Test
    fun `verify KernelSU module metadata and WebUI HTML content generation`() {
        val prop = com.example.module.KernelSuModuleManager.getModuleProp()
        assertTrue(prop.contains("id=controlyst_uinput"))
        assertTrue(prop.contains("webroot=webroot"))

        val webUiHtml = com.example.module.KernelSuModuleManager.getWebUiHtml()
        assertTrue(webUiHtml.contains("Controlyst WebUI"))
        assertTrue(webUiHtml.contains("/dev/uinput"))
        assertTrue(webUiHtml.contains("1000 Hz"))
    }
}
