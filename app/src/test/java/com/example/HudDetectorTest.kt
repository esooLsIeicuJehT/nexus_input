package com.example

import android.graphics.Bitmap
import android.graphics.Color
import com.example.ai.vision.AiHudDetector
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class HudDetectorTest {
    @Test fun requiresScreenshotAndNeverManufacturesGenreLayout() {
        assertTrue(AiHudDetector.detect(null).candidates.isEmpty())
        assertNotNull(AiHudDetector.detect(null).error)
        val bitmap = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.BLACK)
        assertTrue(AiHudDetector.detect(bitmap).candidates.isEmpty())
        bitmap.recycle()
        assertTrue(AiHudDetector.detect(bitmap).candidates.isEmpty())
        assertNotNull(AiHudDetector.detect(bitmap).error)
    }
    @Test fun proposalBoundsComeFromPixelsAndContainNoInferredActionOrInput() {
        val bitmap = Bitmap.createBitmap(200, 100, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.BLACK)
        for (y in 20..35) for (x in 120..140) bitmap.setPixel(x, y, Color.WHITE)
        val candidates = AiHudDetector.detect(bitmap).candidates
        assertEquals(1, candidates.size)
        val candidate = candidates.single()
        assertEquals(.65f, candidate.xNorm, .02f)
        assertEquals(.275f, candidate.yNorm, .02f)
        assertTrue(candidate.recommendedKey.isEmpty())
        assertEquals("Contrast region (assign action)", candidate.predictedAction)
    }
}
