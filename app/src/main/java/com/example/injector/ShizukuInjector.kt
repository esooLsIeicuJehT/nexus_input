package com.example.injector

import android.graphics.PointF
import android.util.Log
import com.example.model.PrivilegeMethod

class ShizukuInjector : InputInjector {
    override val method: PrivilegeMethod = PrivilegeMethod.SHIZUKU

    override fun isAvailable(): Boolean {
        val result = RootController.verifyShizukuAvailable()
        return when (result) {
            is ShellResult.Success -> result.data
            is ShellResult.Failure -> false
        }
    }

    override fun injectTap(x: Float, y: Float): Boolean {
        if (!isAvailable()) {
            throw IllegalStateException("Shizuku service is not running or permission not granted.")
        }
        val cmd = "input tap ${x.toInt()} ${y.toInt()}"
        return executeRishCommand(cmd)
    }

    override fun injectDrag(path: List<PointF>, durationMs: Long): Boolean {
        if (!isAvailable()) {
            throw IllegalStateException("Shizuku service is not running or permission not granted.")
        }
        if (path.size < 2) return false
        val start = path.first()
        val end = path.last()
        val cmd = "input swipe ${start.x.toInt()} ${start.y.toInt()} ${end.x.toInt()} ${end.y.toInt()} $durationMs"
        return executeRishCommand(cmd)
    }

    override fun injectKeyEvent(keyCode: Int, action: Int): Boolean {
        if (!isAvailable()) {
            throw IllegalStateException("Shizuku service is not running or permission not granted.")
        }
        val cmd = "input keyevent $keyCode"
        return executeRishCommand(cmd)
    }

    private fun executeRishCommand(command: String): Boolean {
        val result = RootController.executeShizukuCommand(command)
        return when (result) {
            is ShellResult.Success -> true
            is ShellResult.Failure -> {
                Log.e("ShizukuInjector", "Failed to execute rish command: ${result.message}", result.exception)
                false
            }
        }
    }

    override fun cleanup() {
        Log.d("ShizukuInjector", "Shizuku rish injector session closed")
    }
}
