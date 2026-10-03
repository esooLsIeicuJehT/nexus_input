package com.example.injector

import android.graphics.PointF
import android.util.Log
import com.example.model.PrivilegeMethod

class KernelSUInjector : InputInjector {
    override val method: PrivilegeMethod = PrivilegeMethod.KERNELSU

    override fun isAvailable(): Boolean {
        val result = RootController.verifyRootAvailable()
        return when (result) {
            is ShellResult.Success -> result.data
            is ShellResult.Failure -> false
        }
    }

    override fun injectTap(x: Float, y: Float): Boolean {
        val cmd = "input tap ${x.toInt()} ${y.toInt()}"
        return executeKsuCommand(cmd)
    }

    override fun injectDrag(path: List<PointF>, durationMs: Long): Boolean {
        if (path.size < 2) return false
        val start = path.first()
        val end = path.last()
        val cmd = "input swipe ${start.x.toInt()} ${start.y.toInt()} ${end.x.toInt()} ${end.y.toInt()} $durationMs"
        return executeKsuCommand(cmd)
    }

    override fun injectKeyEvent(keyCode: Int, action: Int): Boolean {
        val cmd = "input keyevent $keyCode"
        return executeKsuCommand(cmd)
    }

    private fun executeKsuCommand(command: String): Boolean {
        val result = RootController.executeSuCommand(command)
        return when (result) {
            is ShellResult.Success -> true
            is ShellResult.Failure -> {
                Log.e("KernelSUInjector", "KernelSU command execution failed: ${result.message}", result.exception)
                false
            }
        }
    }

    override fun cleanup() {
        Log.d("KernelSUInjector", "KernelSU injector cleaned up")
    }
}
