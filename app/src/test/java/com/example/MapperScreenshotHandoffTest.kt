package com.example

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.example.model.MappingConfig
import com.example.ui.mapper.MapperScreenshotHandoff
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.Shadows.shadowOf

/** Pixel and intent fixtures are host tests, not a real Android screenshot/capture result. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MapperScreenshotHandoffTest {
    private val app = ApplicationProvider.getApplicationContext<android.app.Application>()
    private val profile = MappingConfig(id = "capture-test-fixture", profileName = "EXPLICIT TEST FIXTURE", gamePackage = "fixture.game")
    private fun bitmap() = Bitmap.createBitmap(2, 1, Bitmap.Config.ARGB_8888).apply {
        setPixel(0, 0, Color.RED); setPixel(1, 0, Color.BLUE)
    }

    @Test fun writesTheSuppliedPixelsAndKeepsTheExactSavedProfileIdentity() {
        val image = bitmap()
        val intent = MapperScreenshotHandoff.createLaunchIntent(app, image, profile)
        val request = MapperScreenshotHandoff.request(intent, app.packageName).getOrThrow()!!
        assertEquals(profile.id, request.profileId)
        assertEquals(profile.gamePackage, request.gamePackage)
        assertEquals("${app.packageName}.fileprovider", request.uri.authority)
        app.contentResolver.openInputStream(request.uri).use { stream ->
            val decoded = BitmapFactory.decodeStream(stream)
            assertNotNull(decoded)
            assertEquals(Color.RED, decoded.getPixel(0, 0))
            assertEquals(Color.BLUE, decoded.getPixel(1, 0))
        }
        assertFalse(image.isRecycled)
        assertTrue(intent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
        assertEquals(request.uri, intent.clipData!!.getItemAt(0).uri)
    }

    @Test fun launchesOnlyTheActualMapperActivityRequest() {
        assertTrue(MapperScreenshotHandoff.open(app, bitmap(), profile).isSuccess)
        val launched = shadowOf(app).nextStartedActivity
        assertEquals(MainActivity::class.java.name, launched.component!!.className)
        assertEquals(profile.id, MapperScreenshotHandoff.request(launched, app.packageName).getOrThrow()!!.profileId)
    }

    @Test fun unrelatedLauncherIntentHasNoCaptureRequest() {
        assertNull(MapperScreenshotHandoff.request(Intent(Intent.ACTION_MAIN), app.packageName).getOrThrow())
    }

    @Test fun malformedAndExternalRequestsAreErrorsInsteadOfSelectingAnotherProfileOrImage() {
        val valid = MapperScreenshotHandoff.createLaunchIntent(app, bitmap(), profile)
        val missing = Intent(valid).apply { removeExtra("nexus.capture.profile_id") }
        assertTrue(MapperScreenshotHandoff.request(missing, app.packageName).isFailure)
        for (uri in listOf("file:///tmp/old.png", "content://other.provider/all_cache/mapper-captures/nexus-00000000-0000-0000-0000-000000000000.png",
            "content://${app.packageName}.fileprovider/all_cache/modules/old.png",
            "content://${app.packageName}.fileprovider/all_cache/mapper-captures/nexus-invalid.png")) {
            assertTrue(uri, MapperScreenshotHandoff.request(Intent(valid).setData(Uri.parse(uri)), app.packageName).isFailure)
        }
    }

    @Test fun recycledScreenshotAndMissingGameProfileNeverCreateARequest() {
        val image = bitmap().apply { recycle() }
        assertTrue(MapperScreenshotHandoff.open(app, image, profile).isFailure)
        assertTrue(MapperScreenshotHandoff.open(app, bitmap(), profile.copy(gamePackage = "")).isFailure)
        assertNull(shadowOf(app).nextStartedActivity)
    }
}
