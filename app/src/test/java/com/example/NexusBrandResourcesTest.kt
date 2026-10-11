package com.example

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.LayerDrawable
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Exercises Android drawable inflation/drawing on both launcher resource paths. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [24, 34])
class NexusBrandResourcesTest {
    @Test fun regularAndRoundLauncherAssetsInflateForTheActualSdk() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        listOf(R.mipmap.ic_launcher, R.mipmap.ic_launcher_round).forEach { resource ->
            val drawable = requireNotNull(context.getDrawable(resource))
            if (Build.VERSION.SDK_INT >= 26) {
                assertTrue("Modern launchers require an adaptive icon", drawable is AdaptiveIconDrawable)
            } else {
                assertTrue("API 24/25 launchers require the legacy layers", drawable is LayerDrawable)
            }
            val bitmap = Bitmap.createBitmap(108, 108, Bitmap.Config.ARGB_8888)
            drawable.setBounds(0, 0, 108, 108)
            drawable.draw(Canvas(bitmap))
            assertTrue("Launcher must have usable intrinsic dimensions", drawable.intrinsicWidth > 0 && drawable.intrinsicHeight > 0)
            bitmap.recycle()
        }
    }

    @Test fun startupBackgroundAndNexusEmblemDrawWithoutResourceErrors() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        listOf(R.drawable.nexus_launch_background, R.drawable.nexus_logo).forEach { resource ->
            val drawable = requireNotNull(context.getDrawable(resource))
            val bitmap = Bitmap.createBitmap(216, 216, Bitmap.Config.ARGB_8888)
            drawable.setBounds(0, 0, 216, 216)
            drawable.draw(Canvas(bitmap))
            assertTrue("Resolved brand resource must have usable intrinsic dimensions", drawable.intrinsicWidth > 0 && drawable.intrinsicHeight > 0)
            bitmap.recycle()
        }
    }
}
