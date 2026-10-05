package com.example.injector

import android.content.Context
import android.graphics.PointF
import android.util.Log
import com.example.model.PrivilegeMethod
import com.inputmapper.platform.core.InjectionResult
import com.inputmapper.platform.core.TimedTouchPoint
import com.inputmapper.platform.shizuku.ShizukuInjector as RuntimeShizukuInjector

/**
 * Adapter from the existing Nexus injector API to the recovered Shizuku
 * UserService/AIDL implementation. No rish shell fallback is used.
 */
class ShizukuInjector(
    context: Context = NexusRuntimeContext.require()
) : InputInjector {
    override val method: PrivilegeMethod = PrivilegeMethod.SHIZUKU

    private val appContext = context.applicationContext
    private val lock = Any()
    private var runtime: RuntimeShizukuInjector? = null

    override fun isAvailable(): Boolean {
        val access = ShizukuAccess.snapshot(appContext)
        return access.binderAlive && access.permissionGranted
    }

    override fun prepare(): Boolean = synchronized(lock) {
        ensureRuntime() != null
    }

    override fun injectTap(x: Float, y: Float): Boolean = synchronized(lock) {
        val injector = ensureRuntime() ?: return@synchronized false
        result("tap", injector.injectTap(x, y))
    }

    override fun injectDrag(path: List<PointF>, durationMs: Long): Boolean = synchronized(lock) {
        if (path.size < 2 || durationMs <= 0L) return@synchronized false
        val injector = ensureRuntime() ?: return@synchronized false
        val lastIndex = path.lastIndex.coerceAtLeast(1)
        val timed = path.mapIndexed { index, point ->
            val atMillis = if (index == lastIndex) durationMs
            else (durationMs * index.toLong()) / lastIndex.toLong()
            TimedTouchPoint(point.x, point.y, atMillis)
        }
        result("drag", injector.injectDrag(timed, durationMs))
    }

    override fun injectKeyEvent(keyCode: Int, action: Int): Boolean = synchronized(lock) {
        val injector = ensureRuntime() ?: return@synchronized false
        result("key", injector.injectKeyEvent(keyCode, action))
    }

    override fun beginTouch(pointerId: Int, x: Float, y: Float): Boolean = synchronized(lock) {
        val injector = ensureRuntime() ?: return@synchronized false
        result("touch begin", injector.beginTouch(pointerId, x, y))
    }

    override fun moveTouch(pointerId: Int, x: Float, y: Float): Boolean = synchronized(lock) {
        val injector = ensureRuntime() ?: return@synchronized false
        result("touch move", injector.moveTouch(pointerId, x, y))
    }

    override fun endTouch(pointerId: Int): Boolean = synchronized(lock) {
        val injector = runtime ?: return@synchronized false
        result("touch end", injector.endTouch(pointerId))
    }

    override fun cleanup() = synchronized(lock) {
        runtime?.cleanup()?.let { result("cleanup", it) }
        runtime = null
        Unit
    }

    private fun ensureRuntime(): RuntimeShizukuInjector? {
        if (!isAvailable()) {
            val state = ShizukuAccess.snapshot(appContext)
            Log.e(TAG, "Shizuku unavailable: ${state.summary()}")
            return null
        }

        runtime?.let { return it }
        val created = RuntimeShizukuInjector(appContext)
        return when (val connected = created.connect()) {
            InjectionResult.Success -> {
                runtime = created
                Log.i(TAG, "Shizuku UserService connected")
                created
            }
            is InjectionResult.Failure -> {
                Log.e(TAG, "Shizuku connect failed [${connected.code}]: ${connected.message}", connected.cause)
                created.cleanup()
                null
            }
        }
    }

    private fun result(operation: String, result: InjectionResult): Boolean = when (result) {
        InjectionResult.Success -> true
        is InjectionResult.Failure -> {
            Log.e(TAG, "Shizuku $operation failed [${result.code}]: ${result.message}", result.cause)
            false
        }
    }

    private companion object {
        const val TAG = "NexusShizuku"
    }
}
