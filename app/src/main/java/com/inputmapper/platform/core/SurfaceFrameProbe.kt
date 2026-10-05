package com.inputmapper.platform.core

import android.os.Process
import com.example.injector.ProcessShellExecutor
import org.json.JSONObject

/** Fixed read-only SurfaceFlinger commands, executed inside the privileged Binder service. */
object SurfaceFrameProbe {
    fun layers(): String = execute(listOf("/system/bin/dumpsys","SurfaceFlinger","--list"))
    fun latency(layer: String): String {
        require(layer.isNotBlank() && layer.length<=2048 && layer.none { it.isISOControl() }) { "Invalid SurfaceFlinger layer name" }
        return execute(listOf("/system/bin/dumpsys","SurfaceFlinger","--latency",layer))
    }
    private fun execute(arguments: List<String>): String {
        check(Process.myUid() in setOf(0,2000)) { "Frame probe requires actual root or shell UID" }
        val result=ProcessShellExecutor().run(arguments,2000)
        return JSONObject().put("exitCode",result.exitCode ?: JSONObject.NULL).put("stdout",result.stdout)
            .put("stderr",result.stderr).put("timedOut",result.timedOut).put("uid",Process.myUid()).toString()
    }
}
