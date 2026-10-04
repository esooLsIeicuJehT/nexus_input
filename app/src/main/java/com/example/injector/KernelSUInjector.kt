package com.example.injector

import android.content.Context
import android.graphics.PointF
import android.os.Build
import android.util.DisplayMetrics
import android.util.Log
import android.view.WindowManager
import com.example.model.PrivilegeMethod
import com.inputmapper.platform.core.InjectionResult
import com.inputmapper.platform.core.TimedTouchPoint
import com.inputmapper.platform.root.KernelSUInjector as RuntimeKernelSUInjector

/**
 * Adapter from the existing Nexus injector API to the device-verified
 * libsu RootService + JNI /dev/uinput engine recovered from 0.6.2.
 *
 * There is intentionally no shell-input fallback here. If the native backend
 * cannot connect or inject, the failure is surfaced through the return value
 * and logs.
 */
class KernelSUInjector(
    context: Context
) : InputInjector {
    override val method: PrivilegeMethod = PrivilegeMethod.KERNELSU

    private val appContext = context.applicationContext
    private val lock = Any()
    private var runtime: RuntimeKernelSUInjector? = null
    private var runtimeWidth = 0
    private var runtimeHeight = 0

    override fun isAvailable(): Boolean {
        val probe = PrivilegeDetector(appContext).probeAll()
            .firstOrNull { it.method == PrivilegeMethod.KERNELSU }
        return probe?.isDetected == true
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
            val atMillis = if (index == lastIndex) {
                durationMs
            } else {
                (durationMs * index.toLong()) / lastIndex.toLong()
            }
            TimedTouchPoint(point.x, point.y, atMillis)
        }
        result("drag", injector.injectDrag(timed, durationMs))
    }

    override fun injectKeyEvent(keyCode: Int, action: Int): Boolean = synchronized(lock) {
        val injector = ensureRuntime() ?: return@synchronized false
        result("key", injector.injectKeyEvent(keyCode, action))
    }

    override fun cleanup() = synchronized(lock) {
        runtime?.cleanup()?.let { result("cleanup", it) }
        runtime = null
        runtimeWidth = 0
        runtimeHeight = 0
        Unit
    }

    private fun ensureRuntime(): RuntimeKernelSUInjector? {
        if (!isAvailable()) {
            Log.e(TAG, "KernelSU/uinput is not currently available")
            return null
        }

        val geometry = resolveDisplayGeometry() ?: run {
            Log.e(TAG, "Unable to resolve display geometry for virtual touchscreen")
            return null
        }

        val existing = runtime
        if (existing != null && runtimeWidth == geometry.first && runtimeHeight == geometry.second) {
            return existing
        }

        existing?.cleanup()?.let { result("geometry cleanup", it) }
        runtime = null

        val created = RuntimeKernelSUInjector(
            context = appContext,
            width = geometry.first,
            height = geometry.second,
            maxSlots = 10
        )
        return when (val connected = created.connect()) {
            InjectionResult.Success -> {
                runtime = created
                runtimeWidth = geometry.first
                runtimeHeight = geometry.second
                Log.i(TAG, "KernelSU/uinput connected at ${geometry.first}x${geometry.second}: ${created.health()}")
                created
            }
            is InjectionResult.Failure -> {
                Log.e(TAG, "KernelSU/uinput connect failed [${connected.code}]: ${connected.message}", connected.cause)
                created.cleanup()
                null
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun resolveDisplayGeometry(): Pair<Int, Int>? {
        val wm = appContext.getSystemService(WindowManager::class.java) ?: return null
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val bounds = wm.currentWindowMetrics.bounds
            if (bounds.width() > 0 && bounds.height() > 0) bounds.width() to bounds.height() else null
        } else {
            val metrics = DisplayMetrics()
            wm.defaultDisplay.getRealMetrics(metrics)
            if (metrics.widthPixels > 0 && metrics.heightPixels > 0) {
                metrics.widthPixels to metrics.heightPixels
            } else null
        }
    }

    private fun result(operation: String, result: InjectionResult): Boolean = when (result) {
        InjectionResult.Success -> true
        is InjectionResult.Failure -> {
            Log.e(TAG, "KernelSU/uinput $operation failed [${result.code}]: ${result.message}", result.cause)
            false
        }
    }

    private companion object {
        const val TAG = "NexusKernelSU"
    }
}
