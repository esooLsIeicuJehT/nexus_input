package com.example.injector

import android.graphics.PointF
import android.util.Log
import com.example.model.PrivilegeMethod

class APatchInjector : InputInjector {
    override val method: PrivilegeMethod = PrivilegeMethod.APATCH

    override fun isAvailable(): Boolean {
        val result = RootController.verifyRootAvailable()
        return when (result) {
            is ShellResult.Success -> result.data
            is ShellResult.Failure -> false
        }
    }

    override fun injectTap(x: Float, y: Float): Boolean {
        val cmd = "input tap ${x.toInt()} ${y.toInt()}"
        return executeAPatchCommand(cmd)
    }

    override fun injectDrag(path: List<PointF>, durationMs: Long): Boolean {
        if (path.size < 2) return false
        val start = path.first()
        val end = path.last()
        val cmd = "input swipe ${start.x.toInt()} ${start.y.toInt()} ${end.x.toInt()} ${end.y.toInt()} $durationMs"
        return executeAPatchCommand(cmd)
    }

    override fun injectKeyEvent(keyCode: Int, action: Int): Boolean {
        val cmd = "input keyevent $keyCode"
        return executeAPatchCommand(cmd)
    }

    private fun executeAPatchCommand(command: String): Boolean {
        val result = RootController.executeSuCommand(command)
        return when (result) {
            is ShellResult.Success -> true
            is ShellResult.Failure -> {
                Log.e("APatchInjector", "APatch command execution failed: ${result.message}", result.exception)
                false
            }
        }
    }

    override fun cleanup() {
        Log.d("APatchInjector", "APatch injector cleaned up")
    }
}
