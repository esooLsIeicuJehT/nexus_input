package com.example.frames

import org.json.JSONObject
import kotlin.math.ceil

data class PresentedFrameStats(val fps: Double,val meanIntervalMs: Double,val p95IntervalMs: Double,
    val sampleIntervals: Int,val lastPresentNanos: Long,val refreshPeriodNanos: Long)

object SurfaceFrameStats {
    fun commandOutput(encoded: String): String {
        val result=JSONObject(encoded)
        check(result.getInt("uid") in setOf(0,2000)) { "Frame service did not report a privileged UID" }
        check(!result.optBoolean("outputTruncated") && result.isNull("streamError")) { "Frame command output is incomplete" }
        check(!result.getBoolean("timedOut")) { "Frame query timed out" }
        check(!result.isNull("exitCode") && result.getInt("exitCode")==0) {
            "Frame query failed: ${result.optString("stderr")} (exit=${result.opt("exitCode")})"
        }
        return result.getString("stdout")
    }
    fun matchingLayers(output: String,gamePackage: String): List<String> {
        require(gamePackage.matches(Regex("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z0-9_]+)+"))) { "Exact game package is required" }
        val packageMatch=Regex("(?<![A-Za-z0-9_.])${Regex.escape(gamePackage)}(?![A-Za-z0-9_.])")
        return output.lineSequence().map(String::trim).filter { it.isNotEmpty() && packageMatch.containsMatchIn(it) }.distinct().toList()
    }
    fun parse(output: String,nowNanos: Long): PresentedFrameStats {
        val lines=output.lineSequence().filter(String::isNotBlank).toList()
        val refresh=lines.firstOrNull()?.trim()?.toLongOrNull() ?: error("SurfaceFlinger did not provide frame timestamps")
        require(refresh>0) { "Invalid display refresh period" }
        val timestamps=lines.drop(1).mapNotNull { line ->
            val values=line.trim().split(Regex("\\s+"))
            if(values.size!=3) null else values[1].toLongOrNull()?.takeIf { it>0 && it!=Long.MAX_VALUE }
        }.distinct().sorted().takeLast(121)
        require(timestamps.size>=2) { "No measured presented-frame interval is available for this layer" }
        val last=timestamps.last()
        require(last<=nowNanos && nowNanos-last<=3_000_000_000L) { "Presented frames are stale or use an unsupported clock" }
        val intervals=timestamps.zipWithNext { a,b -> (b-a).toDouble()/1_000_000.0 }
        require(intervals.all { it.isFinite() && it>0 }) { "Invalid frame interval" }
        val mean=intervals.average()
        val sorted=intervals.sorted()
        return PresentedFrameStats(1000.0/mean,mean,sorted[(ceil(sorted.size*.95).toInt()-1).coerceAtLeast(0)],intervals.size,last,refresh)
    }
}
