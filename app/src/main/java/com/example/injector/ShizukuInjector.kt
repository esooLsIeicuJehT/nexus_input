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
class ShizukuInjector internal constructor(
    context: Context,
    private val runtimeFactory: (Context) -> RuntimeShizukuInjector,
    private val availabilityCheck: (Context) -> Boolean
) : InputInjector {
    constructor(context: Context = NexusRuntimeContext.require()) : this(
        context,
        { RuntimeShizukuInjector(it) },
        { ShizukuAccess.snapshot(it).let { state -> state.binderAlive && state.permissionGranted } }
    )
    override val method: PrivilegeMethod = PrivilegeMethod.SHIZUKU

    private val appContext = context.applicationContext
    private val lock = Any()
    @Volatile private var runtime: RuntimeShizukuInjector? = null
    private var runtimeReady = false

    override fun isAvailable(): Boolean {
        return availabilityCheck(appContext)
    }

    override fun prepare(): Boolean = synchronized(lock) {
        ensureRuntime() != null
    }

    override fun readinessDetails(): String? = runtime?.connectionDetails?.let {
        "UserService initialized: $it. Game-visible touch delivery is unverified."
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

    override fun readSurfaceLayers(): Result<String> = runtime?.readSurfaceLayers() ?: Result.failure(IllegalStateException("Mapping backend is not prepared"))
    override fun readSurfaceLatency(layer: String): Result<String> = runtime?.readSurfaceLatency(layer) ?: Result.failure(IllegalStateException("Mapping backend is not prepared"))

    override fun cleanup() = synchronized(lock) {
        runtimeReady = false
        runtime?.cleanup()?.let { check(result("cleanup", it)) { "Shizuku cleanup was rejected; inspect backend logs" } }
        runtime = null
        Unit
    }

    private fun ensureRuntime(): RuntimeShizukuInjector? {
        if (!isAvailable()) {
            val state = ShizukuAccess.snapshot(appContext)
            Log.e(TAG, "Shizuku unavailable: ${state.summary()}")
            return null
        }

        runtime?.let { return if (runtimeReady) it else null }
        val created = runtimeFactory(appContext)
        return when (val connected = created.connect()) {
            InjectionResult.Success -> {
                runtime = created
                runtimeReady = true
                Log.i(TAG, "Shizuku UserService initialized: ${created.connectionDetails}; game-visible touch delivery is unverified")
                created
            }
            is InjectionResult.Failure -> {
                Log.e(TAG, "Shizuku connect failed [${connected.code}]: ${connected.message}", connected.cause)
                val cleanup = created.cleanup()
                if (cleanup is InjectionResult.Failure) {
                    // Keep the failed resource available to the caller's cleanup/panic retry.
                    runtime = created
                    throw IllegalStateException("Shizuku preparation failed and cleanup remains unconfirmed: ${cleanup.message}", cleanup.cause)
                }
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
