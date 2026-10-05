package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.diagnostics.ReleaseDiagnostics
import com.example.service.MappingRuntimeBridge
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class) @Config(sdk=[34])
class DiagnosticsTest {
    @Test fun reportExposesFailuresAndDoesNotClaimHardwareVerificationOrInjectedTouches() = runBlocking {
        MappingRuntimeBridge.disarm("Transport unavailable")
        val report=JSONObject(ReleaseDiagnostics.collect(ApplicationProvider.getApplicationContext<Context>()))
        assertEquals(BuildConfig.VERSION_NAME,report.getString("version"))
        assertEquals("Transport unavailable",report.getJSONObject("runtime").getString("error"))
        assertFalse(report.getJSONObject("runtime").getBoolean("backendReady"))
        assertTrue(report.getString("hardwareVerification").contains("Not established"))
        assertFalse(report.getJSONObject("capture").getBoolean("connected"))
        assertEquals(5,report.getJSONArray("backends").length())
        val apatch=(0 until 5).map { report.getJSONArray("backends").getJSONObject(it) }.first { it.getString("method")=="APATCH" }
        assertNotEquals("AVAILABLE",apatch.getString("state"))
        MappingRuntimeBridge.disarm()
    }
}
