package com.inputmapper.platform.root

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.RemoteException
import android.os.SystemClock
import com.inputmapper.platform.core.InjectionErrorCode
import com.inputmapper.platform.core.InjectionResult
import com.inputmapper.platform.core.InputInjector
import com.inputmapper.platform.core.TimedTouchPoint
import com.inputmapper.platform.core.validateKeyAction
import com.inputmapper.platform.core.validateTap
import com.topjohnwu.superuser.ipc.RootService
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * RootService client for Android InputManager injection.
 *
 * This backend never creates /dev/uinput devices and never falls back to shell input.
 * It is intentionally separate from RootUinputInjector so display rotation cannot trigger
 * virtual-device recreation in the KernelSU InputManager path.
 */
internal class RootInputManagerInjector(
    context: Context,
    private val maxPointers: Int,
    override val backendName: String = "KernelSU/InputManager"
) : InputInjector {
    private val appContext = context.applicationContext
    private val lock = Any()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val serviceIntent = Intent(appContext, RootInputService::class.java)
        .addCategory(RootService.CATEGORY_DAEMON_MODE)

    @Volatile private var service: IRootInputService? = null
    @Volatile private var pendingBind: CountDownLatch? = null
    @Volatile private var bound = false
    @Volatile private var lastStatus = "not connected"

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, binder: IBinder) {
            service = IRootInputService.Stub.asInterface(binder)
            bound = true
            pendingBind?.countDown()
        }

        override fun onServiceDisconnected(name: ComponentName) {
            service = null
            bound = false
            lastStatus = "root service disconnected: $name"
        }

        override fun onBindingDied(name: ComponentName) {
            service = null
            bound = false
            lastStatus = "root service binding died: $name"
            pendingBind?.countDown()
        }

        override fun onNullBinding(name: ComponentName) {
            service = null
            bound = false
            lastStatus = "root service returned null binding: $name"
            pendingBind?.countDown()
        }
    }

    fun connect(timeoutMillis: Long = 20_000): InjectionResult = synchronized(lock) {
        if (maxPointers !in 1..32) {
            return@synchronized InjectionResult.Failure(
                InjectionErrorCode.INVALID_ARGUMENT,
                "maxPointers=$maxPointers outside 1..32"
            )
        }

        service?.let { return@synchronized prepare(it) }

        val latch = CountDownLatch(1)
        pendingBind = latch
        mainHandler.post {
            try {
                RootService.bind(serviceIntent, connection)
            } catch (t: Throwable) {
                lastStatus = "RootService.bind failed: ${describe(t)}"
                latch.countDown()
            }
        }

        val signaled = try {
            latch.await(timeoutMillis, TimeUnit.MILLISECONDS)
        } catch (error: InterruptedException) {
            Thread.currentThread().interrupt()
            pendingBind = null
            return@synchronized InjectionResult.Failure(
                InjectionErrorCode.IO_FAILURE,
                "Interrupted while waiting for root service binding",
                error
            )
        }
        pendingBind = null

        val remote = service
        if (!signaled || remote == null) {
            return@synchronized InjectionResult.Failure(
                InjectionErrorCode.PERMISSION_DENIED,
                "Root service did not connect within ${timeoutMillis}ms. Confirm Nexus Input has root permission. Last state: $lastStatus"
            )
        }
        prepare(remote)
    }

    fun health(): String = synchronized(lock) {
        val remote = service ?: return@synchronized lastStatus
        try {
            remote.status().also { lastStatus = it }
        } catch (t: Throwable) {
            "status failed: ${describe(t)}".also { lastStatus = it }
        }
    }

    override fun injectTap(x: Float, y: Float): InjectionResult = synchronized(lock) {
        validateTap(x, y)?.let { return@synchronized it }
        if (x < 0f || y < 0f) return@synchronized invalidCoordinates(x, y)
        val remote = service ?: return@synchronized notReady()
        val pointerId = maxPointers - 1
        callRemote("tap down") { remote.inputManagerTouchDown(pointerId, x, y) }.let {
            if (it !is InjectionResult.Success) return@synchronized it
        }
        SystemClock.sleep(35L)
        callRemote("tap up") { remote.inputManagerTouchUp(pointerId) }
    }

    override fun injectDrag(path: List<TimedTouchPoint>, durationMillis: Long): InjectionResult = synchronized(lock) {
        if (path.size < 2 || durationMillis <= 0L) {
            return@synchronized InjectionResult.Failure(
                InjectionErrorCode.INVALID_ARGUMENT,
                "Drag requires at least two points and durationMillis > 0"
            )
        }
        if (path.first().atMillis != 0L || path.last().atMillis > durationMillis ||
            path.zipWithNext().any { it.second.atMillis < it.first.atMillis }) {
            return@synchronized InjectionResult.Failure(
                InjectionErrorCode.INVALID_ARGUMENT,
                "Drag timestamps must start at 0, be monotonic, and end at or before durationMillis"
            )
        }
        path.forEach { point ->
            validateTap(point.x, point.y)?.let { return@synchronized it }
            if (point.x < 0f || point.y < 0f) return@synchronized invalidCoordinates(point.x, point.y)
        }

        val remote = service ?: return@synchronized notReady()
        val pointerId = maxPointers - 1
        val start = SystemClock.uptimeMillis()
        val first = path.first()
        callRemote("drag down") { remote.inputManagerTouchDown(pointerId, first.x, first.y) }.let {
            if (it !is InjectionResult.Success) return@synchronized it
        }

        for (point in path.drop(1)) {
            val wait = start + point.atMillis - SystemClock.uptimeMillis()
            if (wait > 0L) SystemClock.sleep(wait)
            callRemote("drag move") { remote.inputManagerTouchMove(pointerId, point.x, point.y) }.let {
                if (it !is InjectionResult.Success) {
                    runCatching { remote.inputManagerTouchUp(pointerId) }
                    return@synchronized it
                }
            }
        }
        val remaining = start + durationMillis - SystemClock.uptimeMillis()
        if (remaining > 0L) SystemClock.sleep(remaining)
        callRemote("drag up") { remote.inputManagerTouchUp(pointerId) }
    }

    override fun beginTouch(pointerId: Int, x: Float, y: Float): InjectionResult = synchronized(lock) {
        validatePointer(pointerId, x, y)?.let { return@synchronized it }
        val remote = service ?: return@synchronized notReady()
        callRemote("touch down") { remote.inputManagerTouchDown(pointerId, x, y) }
    }

    override fun moveTouch(pointerId: Int, x: Float, y: Float): InjectionResult = synchronized(lock) {
        validatePointer(pointerId, x, y)?.let { return@synchronized it }
        val remote = service ?: return@synchronized notReady()
        callRemote("touch move") { remote.inputManagerTouchMove(pointerId, x, y) }
    }

    override fun endTouch(pointerId: Int): InjectionResult = synchronized(lock) {
        if (pointerId !in 0 until maxPointers) {
            return@synchronized InjectionResult.Failure(
                InjectionErrorCode.INVALID_ARGUMENT,
                "pointerId=$pointerId outside 0..${maxPointers - 1}"
            )
        }
        val remote = service ?: return@synchronized notReady()
        callRemote("touch up") { remote.inputManagerTouchUp(pointerId) }
    }

    override fun injectKeyEvent(keyCode: Int, action: Int): InjectionResult = synchronized(lock) {
        validateKeyAction(action)?.let { return@synchronized it }
        val remote = service ?: return@synchronized notReady()
        callRemote("key") { remote.inputManagerKey(keyCode, action) }
    }

    override fun publishRuntimeState(state: String): InjectionResult = synchronized(lock) {
        val remote = service ?: return@synchronized notReady()
        callRemote("publish runtime state") { remote.publishState(state) }
    }

    fun readSurfaceLayers(): Result<String> =
        runCatching { (service ?: error("Root frame service is not connected")).readSurfaceLayers() }

    fun readSurfaceLatency(layer: String): Result<String> =
        runCatching { (service ?: error("Root frame service is not connected")).readSurfaceLatency(layer) }

    override fun cleanup(): InjectionResult = synchronized(lock) {
        val remote = service
        val releaseResult = if (remote == null) InjectionResult.Success
        else callRemote("release InputManager contacts") { remote.inputManagerReleaseAll() }

        service = null
        lastStatus = "disconnected"
        val wasBound = bound
        bound = false
        if (wasBound) {
            mainHandler.post {
                runCatching { RootService.unbind(connection) }
                runCatching { RootService.stop(serviceIntent) }
            }
        }

        if (releaseResult is InjectionResult.Success) releaseResult
        else {
            val failure = releaseResult as InjectionResult.Failure
            InjectionResult.Failure(
                InjectionErrorCode.CLEANUP_FAILURE,
                "Root InputManager cleanup failed: ${failure.message}",
                failure.cause
            )
        }
    }

    private fun prepare(remote: IRootInputService): InjectionResult = try {
        lastStatus = remote.status()
        parseReply("prepare InputManager", remote.prepareInputManager(maxPointers)).also {
            if (it is InjectionResult.Success) lastStatus = remote.status()
        }
    } catch (t: Throwable) {
        InjectionResult.Failure(
            InjectionErrorCode.REMOTE_FAILURE,
            "Root InputManager prepare failed: ${describe(t)}",
            t
        )
    }

    private inline fun callRemote(operation: String, block: () -> String): InjectionResult = try {
        parseReply(operation, block())
    } catch (t: Throwable) {
        if (t is RemoteException) {
            service = null
            bound = false
        }
        InjectionResult.Failure(
            InjectionErrorCode.REMOTE_FAILURE,
            "$operation failed over root Binder: ${describe(t)}",
            t
        )
    }

    private fun parseReply(operation: String, reply: String): InjectionResult {
        lastStatus = reply
        if (reply == "OK" || reply.startsWith("OK ")) return InjectionResult.Success
        val code = when {
            reply.contains("NOT_ROOT") || reply.contains("SecurityException") -> InjectionErrorCode.PERMISSION_DENIED
            reply.contains("NOT_READY") -> InjectionErrorCode.NOT_READY
            reply.contains("INVALID_ARGUMENT") -> InjectionErrorCode.INVALID_ARGUMENT
            reply.contains("CONTRACT") || reply.contains("UNSUPPORTED") -> InjectionErrorCode.UNSUPPORTED
            else -> InjectionErrorCode.REMOTE_FAILURE
        }
        return InjectionResult.Failure(code, "$operation: $reply")
    }

    private fun validatePointer(pointerId: Int, x: Float, y: Float): InjectionResult.Failure? {
        validateTap(x, y)?.let { return it }
        if (pointerId !in 0 until maxPointers) {
            return InjectionResult.Failure(
                InjectionErrorCode.INVALID_ARGUMENT,
                "pointerId=$pointerId outside 0..${maxPointers - 1}"
            )
        }
        if (x < 0f || y < 0f) return invalidCoordinates(x, y)
        return null
    }

    private fun invalidCoordinates(x: Float, y: Float) = InjectionResult.Failure(
        InjectionErrorCode.INVALID_ARGUMENT,
        "Touch coordinates must be non-negative: x=$x y=$y"
    )

    private fun notReady() = InjectionResult.Failure(
        InjectionErrorCode.NOT_READY,
        "$backendName root service is not connected; call connect() and handle its result first. Last state: $lastStatus"
    )

    private fun describe(t: Throwable): String =
        "${t.javaClass.simpleName}: ${t.message ?: "unknown"}"
}
