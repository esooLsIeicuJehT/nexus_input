package com.example

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Pixel assertions use native rendering, independently of legacy drawable-inflation tests. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class NexusBrandNativeRenderTest {
    @Test fun launcherSplashAndEmblemAssetsContainActualVisibleArtwork() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        listOf(R.mipmap.ic_launcher, R.mipmap.ic_launcher_round,
            R.drawable.nexus_launch_background, R.drawable.nexus_logo).forEach { resource ->
            val drawable = requireNotNull(context.getDrawable(resource))
            val bitmap = Bitmap.createBitmap(216, 216, Bitmap.Config.ARGB_8888)
            drawable.setBounds(0, 0, 216, 216)
            drawable.draw(Canvas(bitmap))
            val pixels = IntArray(216 * 216)
            bitmap.getPixels(pixels, 0, 216, 0, 0, 216, 216)
            assertTrue("Brand resource $resource must contain visible pixels", pixels.any { it ushr 24 > 0 })
            assertTrue("Brand resource $resource must contain tonal variation", pixels.distinct().size > 1)
            bitmap.recycle()
        }
    }
}
