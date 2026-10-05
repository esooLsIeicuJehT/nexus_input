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
    fun `verify app name resource is NEXUS INPUT`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("NEXUS INPUT", appName)
    }

    @Test
    fun `verify privilege detector probes accessibility fallback`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val detector = PrivilegeDetector(context)
        val probes = kotlinx.coroutines.runBlocking { detector.probeAll() }

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
        val originalConfig = com.example.model.MappingConfig(id="test_profile",profileName="Test",gamePackage="com.test.game",buttons=listOf(com.example.model.MappingNode("test_button",.2f,.3f)))
        val jsonStr = ControlystRepository.serializeConfigToJson(originalConfig)

        assertTrue(jsonStr.contains("schemaVersion"))
        assertTrue(jsonStr.contains("test_profile"))
        assertTrue(jsonStr.contains("test_button"))

        val deserialized = ControlystRepository.deserializeJsonToConfig(jsonStr)
        assertEquals(originalConfig.id, deserialized.id)
        assertEquals(originalConfig.gamePackage, deserialized.gamePackage)
        assertEquals(originalConfig.buttons.size, deserialized.buttons.size)
        assertEquals(originalConfig.crosshair.shape, deserialized.crosshair.shape)
    }

}
