package com.example

import com.example.frames.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Actual command-format fixtures test parsing; no measured hardware FPS is claimed. */
@RunWith(RobolectricTestRunner::class) @Config(sdk=[34])
class SurfaceFrameStatsTest {
    @Test fun fpsAndIntervalsAreCalculatedFromPresentedTimestampsNotRefreshRate() {
        val result=SurfaceFrameStats.parse("8333333\n1 1000000000 1\n2 1020000000 2\n3 1060000000 3\n0 0 0\n0 9223372036854775807 0",1070000000)
        assertEquals(1000.0/30.0,result.fps,.00001)
        assertEquals(30.0,result.meanIntervalMs,0.0);assertEquals(40.0,result.p95IntervalMs,0.0)
        assertEquals(2,result.sampleIntervals);assertEquals(8333333,result.refreshPeriodNanos)
    }
    @Test fun missingStaleMalformedAndUnprivilegedResponsesNeverProduceMeasurements() {
        for(text in listOf("","permission denied","8333333\n0 0 0","0\n1 100 1\n2 200 2")) assertTrue(runCatching { SurfaceFrameStats.parse(text,1000) }.isFailure)
        assertTrue(runCatching { SurfaceFrameStats.parse("8333333\n1 100 1\n2 200 2",5000000000) }.isFailure)
        for(result in listOf("{\"uid\":10000,\"timedOut\":false,\"exitCode\":0,\"stdout\":\"\"}","{\"uid\":2000,\"timedOut\":true,\"exitCode\":0,\"stdout\":\"\"}","{\"uid\":2000,\"timedOut\":false,\"exitCode\":1,\"stdout\":\"\",\"stderr\":\"denied\"}")) {
            assertTrue(runCatching { SurfaceFrameStats.commandOutput(result) }.isFailure)
        }
        assertEquals("observed",SurfaceFrameStats.commandOutput("{\"uid\":2000,\"timedOut\":false,\"exitCode\":0,\"stdout\":\"observed\"}"))
    }
    @Test fun layerSelectionUsesExactPackageBoundariesAndNeverInventsAName() {
        val layers=SurfaceFrameStats.matchingLayers("SurfaceView[com.test.game/Activity]\ncom.test.game.other/Main\nUnrelated\nSurfaceView[com.test.game/Activity]","com.test.game")
        assertEquals(listOf("SurfaceView[com.test.game/Activity]"),layers)
        assertTrue(SurfaceFrameStats.matchingLayers("Unrelated","com.test.game").isEmpty())
        assertTrue(runCatching { SurfaceFrameStats.matchingLayers("Anything","bad") }.isFailure)
        assertEquals("No data",FrameMonitor.label(FrameMonitorState(error="No data")))
    }
}
