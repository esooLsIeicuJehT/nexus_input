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
import android.view.InputDevice
import android.view.KeyEvent
import com.inputmapper.platform.core.InjectionErrorCode
import com.inputmapper.platform.core.InjectionResult
import com.inputmapper.platform.core.InputInjector
import com.inputmapper.platform.core.TimedTouchPoint
import com.inputmapper.platform.core.validateKeyAction
import com.inputmapper.platform.core.validateTap
import com.topjohnwu.superuser.ipc.RootService
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

internal class RootUinputInjector(
    context: Context,
    private val width: Int,
    private val height: Int,
    private val maxSlots: Int,
    override val backendName: String,
    private val useInputManager: Boolean = false
) : InputInjector {
    private val appContext = context.applicationContext
    private val lock = Any()
    private val trackingIds = AtomicInteger(1)
    private val mainHandler = Handler(Looper.getMainLooper())
    private val serviceIntent = Intent(appContext, RootInputService::class.java)
        .addCategory(RootService.CATEGORY_DAEMON_MODE)

    @Volatile private var service: IRootInputService? = null
    @Volatile private var pendingBind: CountDownLatch? = null
    @Volatile private var bound = false
    @Volatile private var lastStatus: String = "not connected"

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
        if (width <= 0 || height <= 0 || maxSlots !in 1..32) {
            return@synchronized InjectionResult.Failure(
                InjectionErrorCode.INVALID_ARGUMENT,
                "Invalid virtual touchscreen geometry ${width}x$height slots=$maxSlots"
            )
        }

        service?.let { return@synchronized prepareBackend(it) }

        val latch = CountDownLatch(1)
        pendingBind = latch
        mainHandler.post {
            try {
                RootService.bind(serviceIntent, connection)
            } catch (t: Throwable) {
                lastStatus = "RootService.bind failed: ${t.javaClass.simpleName}: ${t.message ?: "unknown"}"
                latch.countDown()
            }
        }

        val signaled = try {
            latch.await(timeoutMillis, TimeUnit.MILLISECONDS)
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            pendingBind = null
            return@synchronized InjectionResult.Failure(
                InjectionErrorCode.IO_FAILURE,
                "Interrupted while waiting for root service binding",
                e
            )
        }
        pendingBind = null

        val connected = service
        if (!signaled || connected == null) {
            return@synchronized InjectionResult.Failure(
                InjectionErrorCode.PERMISSION_DENIED,
                "Root service did not connect within ${timeoutMillis}ms. Confirm this app is granted root in KernelSU/Magisk. Last state: $lastStatus"
            )
        }
        prepareBackend(connected)
    }

    fun health(): String = synchronized(lock) {
        val remote = service ?: return@synchronized lastStatus
        try {
            remote.status().also { lastStatus = it }
        } catch (t: Throwable) {
            "status failed: ${remoteMessage(t)}".also { lastStatus = it }
        }
    }

    override fun injectTap(x: Float, y: Float): InjectionResult = synchronized(lock) {
        validateTap(x, y)?.let { return@synchronized it }
        ensureCoordinate(x, y)?.let { return@synchronized it }
        val remote = service ?: return@synchronized notReady()
        val id = nextTrackingId()
        callRemote("touch down") { remote.touchDown(0, id, x.toInt(), y.toInt()) }.let {
            if (it !is InjectionResult.Success) return@synchronized it
        }
        SystemClock.sleep(35L)
        callRemote("touch up") { remote.touchUp(0) }
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
        path.forEach {
            validateTap(it.x, it.y)?.let { failure -> return@synchronized failure }
            ensureCoordinate(it.x, it.y)?.let { failure -> return@synchronized failure }
        }
        val remote = service ?: return@synchronized notReady()

        val start = SystemClock.uptimeMillis()
        val first = path.first()
        callRemote("drag down") {
            remote.touchDown(0, nextTrackingId(), first.x.toInt(), first.y.toInt())
        }.let { if (it !is InjectionResult.Success) return@synchronized it }

        for (point in path.drop(1)) {
            val wait = start + point.atMillis - SystemClock.uptimeMillis()
            if (wait > 0) SystemClock.sleep(wait)
            callRemote("drag move") { remote.touchMove(0, point.x.toInt(), point.y.toInt()) }.let {
                if (it !is InjectionResult.Success) {
                    runCatching { remote.touchUp(0) }
                    return@synchronized it
                }
            }
        }
        val remaining = start + durationMillis - SystemClock.uptimeMillis()
        if (remaining > 0) SystemClock.sleep(remaining)
        callRemote("drag up") { remote.touchUp(0) }
    }

    override fun beginTouch(pointerId: Int, x: Float, y: Float): InjectionResult = synchronized(lock) {
        validateTap(x, y)?.let { return@synchronized it }
        ensureCoordinate(x, y)?.let { return@synchronized it }
        if (pointerId !in 0 until maxSlots) {
            return@synchronized InjectionResult.Failure(InjectionErrorCode.INVALID_ARGUMENT, "pointerId=$pointerId outside 0..${maxSlots - 1}")
        }
        val remote = service ?: return@synchronized notReady()
        if (useInputManager) callRemote("touch down") { remote.inputManagerTouchDown(pointerId, x, y) }
        else callRemote("touch down") { remote.touchDown(pointerId, nextTrackingId(), x.toInt(), y.toInt()) }
    }

    override fun moveTouch(pointerId: Int, x: Float, y: Float): InjectionResult = synchronized(lock) {
        validateTap(x, y)?.let { return@synchronized it }
        ensureCoordinate(x, y)?.let { return@synchronized it }
        if (pointerId !in 0 until maxSlots) {
            return@synchronized InjectionResult.Failure(InjectionErrorCode.INVALID_ARGUMENT, "pointerId=$pointerId outside 0..${maxSlots - 1}")
        }
        val remote = service ?: return@synchronized notReady()
        if (useInputManager) callRemote("touch move") { remote.inputManagerTouchMove(pointerId, x, y) }
        else callRemote("touch move") { remote.touchMove(pointerId, x.toInt(), y.toInt()) }
    }

    override fun endTouch(pointerId: Int): InjectionResult = synchronized(lock) {
        if (pointerId !in 0 until maxSlots) {
            return@synchronized InjectionResult.Failure(InjectionErrorCode.INVALID_ARGUMENT, "pointerId=$pointerId outside 0..${maxSlots - 1}")
        }
        val remote = service ?: return@synchronized notReady()
        if (useInputManager) callRemote("touch up") { remote.inputManagerTouchUp(pointerId) }
        else callRemote("touch up") { remote.touchUp(pointerId) }
    }

    override fun injectKeyEvent(keyCode: Int, action: Int): InjectionResult = synchronized(lock) {
        validateKeyAction(action)?.let { return@synchronized it }
        val remote = service ?: return@synchronized notReady()
        if (useInputManager) return@synchronized callRemote("key") { remote.inputManagerKey(keyCode, action) }
        val linuxCode = LinuxKeyCodeMapper.fromAndroid(keyCode)
            ?: return@synchronized InjectionResult.Failure(
                InjectionErrorCode.UNSUPPORTED,
                "No verified Android-to-Linux key mapping for keyCode=$keyCode"
            )
        val value = if (action == KeyEvent.ACTION_DOWN) 1 else 0
        callRemote("key") { remote.key(linuxCode, value) }
    }

    override fun publishRuntimeState(state: String): InjectionResult = synchronized(lock) {
        val remote = service ?: return@synchronized notReady()
        callRemote("publish runtime state") { remote.publishState(state) }
    }

    fun readSurfaceLayers(): Result<String> = runCatching { (service ?: error("Root frame service is not connected")).readSurfaceLayers() }
    fun readSurfaceLatency(layer:String): Result<String> = runCatching { (service ?: error("Root frame service is not connected")).readSurfaceLatency(layer) }

    override fun cleanup(): InjectionResult = synchronized(lock) {
        val remote = service
        val destroyResult = if (remote == null) {
            InjectionResult.Success
        } else {
            if (useInputManager) callRemote("release InputManager contacts") { remote.inputManagerReleaseAll() }
            else callRemote("destroy devices") { remote.destroyDevices() }
        }

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

        if (destroyResult is InjectionResult.Success) {
            destroyResult
        } else {
            val failure = destroyResult as InjectionResult.Failure
            InjectionResult.Failure(
                InjectionErrorCode.CLEANUP_FAILURE,
                "Root service cleanup failed: ${failure.message}",
                failure.cause
            )
        }
    }

    private fun prepareBackend(remote: IRootInputService): InjectionResult {
        if (useInputManager) return prepareInputManager(remote)
        return createDevices(remote)
    }

    private fun prepareInputManager(remote: IRootInputService): InjectionResult = try {
        lastStatus = remote.status()
        parseReply("prepare InputManager", remote.prepareInputManager(maxSlots)).also {
            if (it is InjectionResult.Success) lastStatus = remote.status()
        }
    } catch (t: Throwable) {
        InjectionResult.Failure(InjectionErrorCode.REMOTE_FAILURE, "Root InputManager prepare failed: ${remoteMessage(t)}", t)
    }

    private fun createDevices(remote: IRootInputService): InjectionResult {
        return try {
            val status = remote.status()
            lastStatus = status
            val createResult = parseReply("create uinput devices", remote.create(width, height, maxSlots))
            if (createResult !is InjectionResult.Success) return createResult

            if (!waitForAndroidInputDevice("InputMapper Virtual Touchscreen", 2_500L)) {
                runCatching { remote.destroyDevices() }
                return InjectionResult.Failure(
                    InjectionErrorCode.NOT_READY,
                    "Kernel created the uinput devices, but Android InputReader did not expose InputMapper Virtual Touchscreen within 2500ms. Devices were cleaned up instead of pretending injection succeeded."
                )
            }
            lastStatus = remote.status()
            InjectionResult.Success
        } catch (t: Throwable) {
            InjectionResult.Failure(
                InjectionErrorCode.REMOTE_FAILURE,
                "Root service create failed: ${remoteMessage(t)}",
                t
            )
        }
    }

    private fun waitForAndroidInputDevice(name: String, timeoutMillis: Long): Boolean {
        val deadline = SystemClock.uptimeMillis() + timeoutMillis
        do {
            val found = InputDevice.getDeviceIds().any { id -> InputDevice.getDevice(id)?.name == name }
            if (found) return true
            SystemClock.sleep(50L)
        } while (SystemClock.uptimeMillis() < deadline)
        return false
    }

    private inline fun callRemote(operation: String, block: () -> String): InjectionResult {
        return try {
            parseReply(operation, block())
        } catch (t: Throwable) {
            if (t is RemoteException) {
                service = null
                bound = false
            }
            InjectionResult.Failure(
                InjectionErrorCode.REMOTE_FAILURE,
                "$operation failed over root Binder: ${remoteMessage(t)}",
                t
            )
        }
    }

    private fun parseReply(operation: String, reply: String): InjectionResult {
        lastStatus = reply
        return if (reply == "OK" || reply.startsWith("OK ")) {
            InjectionResult.Success
        } else {
            val code = when {
                reply.contains("NOT_ROOT") -> InjectionErrorCode.PERMISSION_DENIED
                reply.contains("NOT_READY") -> InjectionErrorCode.NOT_READY
                reply.contains("NATIVE_") -> InjectionErrorCode.NATIVE_FAILURE
                else -> InjectionErrorCode.REMOTE_FAILURE
            }
            InjectionResult.Failure(code, "$operation: $reply")
        }
    }

    private fun remoteMessage(t: Throwable): String = "${t.javaClass.simpleName}: ${t.message ?: "unknown"}"

    private fun notReady() = InjectionResult.Failure(
        InjectionErrorCode.NOT_READY,
        "$backendName root service is not connected; call connect() and handle its result first. Last state: $lastStatus"
    )

    private fun ensureCoordinate(x: Float, y: Float): InjectionResult.Failure? {
        if (x < 0 || y < 0 || x >= width || y >= height) {
            return InjectionResult.Failure(
                InjectionErrorCode.INVALID_ARGUMENT,
                "Coordinate ($x,$y) is outside configured display ${width}x$height"
            )
        }
        return null
    }

    private fun nextTrackingId(): Int {
        val next = trackingIds.getAndIncrement()
        return if (next in 1..65535) next else {
            trackingIds.set(2)
            1
        }
    }
}
