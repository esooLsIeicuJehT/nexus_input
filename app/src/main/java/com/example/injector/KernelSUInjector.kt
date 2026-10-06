package com.example.injector

import android.content.Context
import android.graphics.PointF
import android.util.Log
import com.example.model.PrivilegeMethod
import com.inputmapper.platform.core.InjectionResult
import com.inputmapper.platform.core.TimedTouchPoint
import com.inputmapper.platform.root.KernelSUInjector as RuntimeKernelSUInjector

/**
 * Adapter from the Nexus mapper API to the libsu RootService + privileged
 * Android InputManager backend.
 *
 * KernelSU touch injection does not create a virtual touchscreen, does not depend
 * on display geometry during preparation, and has no shell/uinput fallback.
 */
class KernelSUInjector(
    context: Context = NexusRuntimeContext.require()
) : InputInjector {
    override val method: PrivilegeMethod = PrivilegeMethod.KERNELSU

    private val appContext = context.applicationContext
    private val lock = Any()
    @Volatile private var runtime: RuntimeKernelSUInjector? = null
    @Volatile private var lastPrepareFailure: String? = null

    override fun isAvailable(): Boolean {
        val probe = PrivilegeDetector(appContext).probeAll()
            .firstOrNull { it.method == PrivilegeMethod.KERNELSU }
        return probe?.isDetected == true
    }

    override fun prepare(): Boolean = synchronized(lock) {
        if (ensureRuntime() != null) return@synchronized true
        throw IllegalStateException(lastPrepareFailure ?: "KernelSU/InputManager preparation failed without a diagnostic")
    }

    override fun readinessDetails(): String? = runtime?.let {
        "KernelSU/InputManager initialized: ${it.health()}. Game-visible touch delivery is unverified."
    } ?: lastPrepareFailure

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

    override fun readSurfaceLayers(): Result<String> = runtime?.readSurfaceLayers()
        ?: Result.failure(IllegalStateException("Mapping backend is not prepared"))

    override fun readSurfaceLatency(layer: String): Result<String> = runtime?.readSurfaceLatency(layer)
        ?: Result.failure(IllegalStateException("Mapping backend is not prepared"))

    override fun cleanup() = synchronized(lock) {
        runtime?.cleanup()?.let {
            check(result("cleanup", it)) { "KernelSU/InputManager cleanup was rejected; inspect backend logs" }
        }
        runtime = null
        Unit
    }

    private fun ensureRuntime(): RuntimeKernelSUInjector? {
        runtime?.let { return it }

        if (!isAvailable()) {
            lastPrepareFailure = "KernelSU is not currently available or root permission is not granted"
            Log.e(TAG, lastPrepareFailure!!)
            return null
        }

        val created = RuntimeKernelSUInjector(
            context = appContext,
            maxSlots = com.example.input.TouchSlotAllocator.MAX_SLOTS
        )
        return when (val connected = created.connect()) {
            InjectionResult.Success -> {
                runtime = created
                lastPrepareFailure = null
                Log.i(TAG, "KernelSU/InputManager connected: ${created.health()}; game-visible touch delivery remains unverified")
                created
            }
            is InjectionResult.Failure -> {
                lastPrepareFailure = "KernelSU/InputManager connect failed [${connected.code}]: ${connected.message}"
                Log.e(TAG, lastPrepareFailure!!, connected.cause)
                when (val cleanup = created.cleanup()) {
                    InjectionResult.Success -> Unit
                    is InjectionResult.Failure -> {
                        val message = "$lastPrepareFailure; cleanup unconfirmed [${cleanup.code}]: ${cleanup.message}"
                        lastPrepareFailure = message
                        throw IllegalStateException(message, cleanup.cause)
                    }
                }
                null
            }
        }
    }

    private fun result(operation: String, result: InjectionResult): Boolean = when (result) {
        InjectionResult.Success -> true
        is InjectionResult.Failure -> {
            Log.e(TAG, "KernelSU/InputManager $operation failed [${result.code}]: ${result.message}", result.cause)
            false
        }
    }

    private companion object {
        const val TAG = "NexusKernelSU"
    }
}
