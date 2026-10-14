package com.example

import android.graphics.Bitmap
import com.example.service.MapperCaptureCoordinator
import com.example.service.awaitMapperBitmap
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.time.Duration.Companion.seconds

/** Operation-boundary fixtures establish ordering/resource ownership, not device screenshot success. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[34])
class MapperCaptureCoordinatorTest {
    private fun image()=Bitmap.createBitmap(2,1,Bitmap.Config.ARGB_8888)

    @Test fun releaseAndAllWindowRemovalPrecedeFramesAndServiceStopPrecedesHandoff() = runTest {
        val calls=mutableListOf<String>()
        val bitmap=image()
        MapperCaptureCoordinator(
            releaseContacts={calls+="release acknowledged"},
            removeOwnedWindows={calls+="mapper, crosshair and FPS removed"},
            awaitFrame={calls+="frame"},
            captureBitmap={calls+="capture";bitmap},
            stopMapping={calls+="service stopped"},
            openMapper={assertSame(bitmap,it);assertFalse(it.isRecycled);calls+="handoff"}
        ).capture()
        assertEquals(listOf("release acknowledged","mapper, crosshair and FPS removed","frame","frame","capture","service stopped","handoff"),calls)
        assertTrue(bitmap.isRecycled)
    }

    @Test fun unacknowledgedReleaseCannotRemoveWindowsCaptureOrOpenMapper() = runTest {
        val calls=mutableListOf<String>()
        val result=runCatching { MapperCaptureCoordinator(
            releaseContacts={error("Explicit test fixture: release rejected")},
            removeOwnedWindows={calls+="hide"},awaitFrame={calls+="frame"},
            captureBitmap={calls+="capture";image()},stopMapping={calls+="stop"},openMapper={calls+="open"}
        ).capture() }
        assertEquals("Explicit test fixture: release rejected",result.exceptionOrNull()?.message)
        assertTrue(calls.isEmpty())
    }

    @Test fun failedOwnedWindowRemovalCannotBecomeACapturedImage() = runTest {
        val calls=mutableListOf<String>()
        val result=runCatching { MapperCaptureCoordinator(
            releaseContacts={},removeOwnedWindows={error("Explicit test fixture: FPS window removal failed")},
            awaitFrame={calls+="frame"},captureBitmap={calls+="capture";image()},
            stopMapping={calls+="stop"},openMapper={calls+="open"}
        ).capture() }
        assertTrue(result.isFailure);assertTrue(calls.isEmpty())
    }

    @Test fun failedCaptureDoesNotOpenAnOldOrReplacementBitmap() = runTest {
        var opened=false
        val result=runCatching { MapperCaptureCoordinator(releaseContacts={},removeOwnedWindows={},awaitFrame={},
            captureBitmap={error("Explicit test fixture: Android rejected protected content")},
            stopMapping={},openMapper={opened=true}).capture() }
        assertEquals("Explicit test fixture: Android rejected protected content",result.exceptionOrNull()?.message)
        assertFalse(opened)
    }

    @Test fun stopFailurePreventsHandoffAndRecyclesTheRealReturnedBitmap() = runTest {
        val bitmap=image();var opened=false
        val result=runCatching { MapperCaptureCoordinator(releaseContacts={},removeOwnedWindows={},awaitFrame={},
            captureBitmap={bitmap},stopMapping={error("Explicit test fixture: mapping stop failed")},openMapper={opened=true}).capture() }
        assertTrue(result.isFailure);assertFalse(opened);assertTrue(bitmap.isRecycled)
    }

    @Test fun failedActivityHandoffRecyclesTheReturnedBitmapAndPreservesTheFailure() = runTest {
        val bitmap=image()
        val result=runCatching { MapperCaptureCoordinator(releaseContacts={},removeOwnedWindows={},awaitFrame={},
            captureBitmap={bitmap},stopMapping={},openMapper={error("Explicit test fixture: activity launch rejected")}).capture() }
        assertEquals("Explicit test fixture: activity launch rejected",result.exceptionOrNull()?.message)
        assertTrue(bitmap.isRecycled)
    }

    @Test fun cancelledCaptureRecyclesLateAndroidCallbackInsteadOfOpeningMapper() = runTest {
        lateinit var callback:(Result<Bitmap>)->Unit
        var opened=false
        val job=launch { MapperCaptureCoordinator(releaseContacts={},removeOwnedWindows={},awaitFrame={},
            captureBitmap={awaitMapperBitmap { callback=it }},stopMapping={},openMapper={opened=true}).capture() }
        runCurrent();job.cancel();runCurrent()
        val bitmap=image();callback(Result.success(bitmap));runCurrent()
        assertFalse(opened);assertTrue(bitmap.isRecycled)
    }

    @Test fun cancellationAfterCallbackBeforeDispatchAlsoRecyclesBitmap() = runTest {
        lateinit var callback:(Result<Bitmap>)->Unit
        var received=false
        val job=launch { awaitMapperBitmap { callback=it };received=true }
        runCurrent()
        val bitmap=image();callback(Result.success(bitmap));job.cancel();runCurrent()
        assertFalse(received);assertTrue(bitmap.isRecycled)
    }

    @Test fun actualCaptureFailureIsPreservedByTheCallbackAdapter() = runTest(timeout = 10.seconds) {
        val failure=IllegalStateException("Explicit test fixture: screenshot API error")
        val result=runCatching { awaitMapperBitmap { it(Result.failure(failure)) } }
        val observed = requireNotNull(result.exceptionOrNull())
        assertEquals(failure.javaClass, observed.javaClass)
        assertEquals(failure.message, observed.message)
        // Coroutine 1.10.2 stack-trace recovery can copy a throwable and retain the
        // original as its cause. Both execution modes must preserve the real failure.
        assertSame(failure, if (observed === failure) observed else observed.cause)
    }
}
