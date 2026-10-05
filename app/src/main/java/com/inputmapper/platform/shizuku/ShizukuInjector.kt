package com.inputmapper.platform.shizuku

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.IBinder
import android.os.SystemClock
import android.os.Build
import android.util.Log
import android.view.KeyEvent
import android.view.MotionEvent
import com.inputmapper.platform.core.InjectionErrorCode
import com.inputmapper.platform.core.InjectionResult
import com.inputmapper.platform.core.InputInjector
import com.inputmapper.platform.core.TimedTouchPoint
import com.inputmapper.platform.core.validateKeyAction
import com.inputmapper.platform.core.validateTap
import rikka.shizuku.Shizuku
import java.util.LinkedHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class ShizukuInjector(
    private val context: Context,
    private val runtime: ShizukuRuntime = RealShizukuRuntime
) : InputInjector {
    override val backendName: String = "Shizuku"

    @Volatile private var remote: IShizukuInputService? = null
    @Volatile private var readyForInput = false
    @Volatile var connectionDetails: String? = null
        private set
    private val connectionLock = Any()
    private var connectionGeneration = 0L
    private var connection: ServiceConnection? = null
    private var args: Shizuku.UserServiceArgs? = null

    private data class Point(var x: Float, var y: Float)
    private val touchLock = Any()
    private val activeTouches = LinkedHashMap<Int, Point>()
    private var touchDownTime = 0L

    fun connect(timeoutMillis: Long = 5_000): InjectionResult {
        return try {
            if (timeoutMillis < 0) return failedConnection(InjectionErrorCode.INVALID_ARGUMENT, "Negative Shizuku bind timeout")
            if (!runtime.pingBinder()) return failedConnection(InjectionErrorCode.BACKEND_UNAVAILABLE, "Shizuku is not running")
            if (runtime.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
                return failedConnection(InjectionErrorCode.PERMISSION_DENIED, "Shizuku permission has not been granted to this app")
            }
            remote?.let { service ->
                val details = verifyService(service)
                connectionDetails = details
                readyForInput = true
                return InjectionResult.Success
            }
            if (synchronized(touchLock) { activeTouches.isNotEmpty() }) {
                return failedConnection(InjectionErrorCode.CLEANUP_FAILURE,
                    "Unconfirmed contacts remain after Shizuku service loss; injection cannot reconnect until release is confirmed")
            }

            // Disconnect an earlier binding before starting a new one; failed unbinds remain
            // observable and retain their handle for cleanup retry.
            discardEmptyConnection()?.let {
                return InjectionResult.Failure(InjectionErrorCode.REMOTE_FAILURE, it)
            }
            val latch = CountDownLatch(1)
            val accepting = AtomicBoolean(true)
            var candidate: IShizukuInputService? = null
            val generation = synchronized(connectionLock) { ++connectionGeneration }
            val localArgs = Shizuku.UserServiceArgs(
                ComponentName(context.packageName, ShizukuInputUserService::class.java.name)
            ).processNameSuffix("mapper_input")
                .tag("input-injector-v2")
                .version(USER_SERVICE_VERSION)
                .daemon(false)
            val localConnection = object : ServiceConnection {
                override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
                    synchronized(connectionLock) {
                        if (generation != connectionGeneration || !accepting.get()) {
                            Log.w(TAG, "Ignored expired Shizuku UserService callback")
                            return
                        }
                        candidate = IShizukuInputService.Stub.asInterface(service)
                    }
                    latch.countDown()
                }

                override fun onServiceDisconnected(name: ComponentName?) {
                    synchronized(connectionLock) {
                        if (generation != connectionGeneration) return
                        accepting.set(false)
                        remote = null
                        readyForInput = false
                        connectionDetails = null
                    }
                    Log.e(TAG, "Shizuku UserService disconnected; injection unavailable, release may be unconfirmed")
                    latch.countDown()
                }
            }
            synchronized(connectionLock) {
                args = localArgs
                connection = localConnection
            }
            runtime.bindUserService(localArgs, localConnection)
            if (!latch.await(timeoutMillis, TimeUnit.MILLISECONDS)) {
                accepting.set(false)
                failedConnection(InjectionErrorCode.REMOTE_FAILURE, "Timed out binding Shizuku UserService")
            } else {
                val service = candidate ?: error("Shizuku service binder missing after connection")
                val details = verifyService(service)
                synchronized(connectionLock) {
                    check(generation == connectionGeneration && accepting.get()) { "Shizuku service disconnected during initialization" }
                    remote = service
                    connectionDetails = details
                    readyForInput = true
                }
                Log.i(TAG, "Shizuku bridge initialized: $details; game-visible touch delivery remains unverified")
                InjectionResult.Success
            }
        } catch (t: Throwable) {
            failedConnection(InjectionErrorCode.REMOTE_FAILURE, "Unable to initialize Shizuku UserService: ${t.javaClass.simpleName}: ${t.message}", t)
        }
    }

    private fun verifyService(service: IShizukuInputService): String {
        check(service.asBinder().isBinderAlive) { "Shizuku UserService binder is dead" }
        val test = service.selfTest()
        val uid = Regex("""\buid=(\d+)\b""").find(test)?.groupValues?.get(1)?.toIntOrNull()
        val sdk = Regex("""\bsdk=(\d+)\b""").find(test)?.groupValues?.get(1)?.toIntOrNull()
        val expectedBridge = FrameworkInputBridge.classNameForSdk(Build.VERSION.SDK_INT).substringAfterLast('.')
        val bridge = Regex("""\bbridge=(\w+)\b""").find(test)?.groupValues?.get(1)
        val version = Regex("""\bserviceVersion=(\d+)\b""").find(test)?.groupValues?.get(1)?.toIntOrNull()
        check(test.startsWith("OK ") && (uid == 0 || uid == 2000) && sdk == Build.VERSION.SDK_INT &&
            bridge == expectedBridge && "mode=WAIT_FOR_RESULT(1)" in test && "probe=BRIDGE_ONLY" in test && version == USER_SERVICE_VERSION) {
            "Shizuku UserService did not confirm the current shell/root bridge and dispatch mode: $test"
        }
        return test
    }

    private fun failedConnection(code: InjectionErrorCode, message: String, cause: Throwable? = null): InjectionResult.Failure {
        readyForInput = false
        connectionDetails = null
        // Preserve the only release transport when acknowledged contacts still need cleanup.
        val unbindError = discardEmptyConnection()
        return InjectionResult.Failure(code, listOfNotNull(message, unbindError).joinToString("; "), cause)
    }

    private fun discardEmptyConnection(): String? {
        if (synchronized(touchLock) { activeTouches.isNotEmpty() }) return null
        val binding = synchronized(connectionLock) {
            ++connectionGeneration
            remote = null
            readyForInput = false
            connectionDetails = null
            args to connection
        }
        val oldArgs = binding.first ?: return null
        val oldConnection = binding.second ?: return null
        return try {
            runtime.unbindUserService(oldArgs, oldConnection, true)
            synchronized(connectionLock) {
                if (args === oldArgs && connection === oldConnection) { args = null; connection = null }
            }
            null
        } catch (error: Throwable) {
            "Shizuku unbind failed: ${error.javaClass.simpleName}: ${error.message}".also { Log.e(TAG, it, error) }
        }
    }

    override fun injectTap(x: Float, y: Float): InjectionResult {
        validateTap(x, y)?.let { return it }
        beginTouch(0, x, y).let { if (it !is InjectionResult.Success) return it }
        SystemClock.sleep(35L)
        return endTouch(0)
    }

    override fun injectDrag(path: List<TimedTouchPoint>, durationMillis: Long): InjectionResult {
        if (path.size < 2 || durationMillis <= 0L) {
            return InjectionResult.Failure(InjectionErrorCode.INVALID_ARGUMENT, "Drag requires at least two points and durationMillis > 0")
        }
        if (path.first().atMillis != 0L || path.last().atMillis > durationMillis || path.zipWithNext().any { it.second.atMillis < it.first.atMillis }) {
            return InjectionResult.Failure(
                InjectionErrorCode.INVALID_ARGUMENT,
                "Drag timestamps must start at 0, be monotonic, and end at or before durationMillis"
            )
        }
        path.forEach { validateTap(it.x, it.y)?.let { failure -> return failure } }
        val start = SystemClock.uptimeMillis()
        beginTouch(0, path.first().x, path.first().y).let { if (it !is InjectionResult.Success) return it }
        for (point in path.drop(1)) {
            val wait = start + point.atMillis - SystemClock.uptimeMillis()
            if (wait > 0) SystemClock.sleep(wait)
            moveTouch(0, point.x, point.y).let {
                if (it !is InjectionResult.Success) {
                    endTouch(0)
                    return it
                }
            }
        }
        val remaining = start + durationMillis - SystemClock.uptimeMillis()
        if (remaining > 0) SystemClock.sleep(remaining)
        return endTouch(0)
    }

    override fun beginTouch(pointerId: Int, x: Float, y: Float): InjectionResult = synchronized(touchLock) {
        validateTap(x, y)?.let { return@synchronized it }
        if (pointerId !in 0..31) {
            return@synchronized InjectionResult.Failure(InjectionErrorCode.INVALID_ARGUMENT, "pointerId=$pointerId outside 0..31")
        }
        if(activeTouches.containsKey(pointerId) || activeTouches.size>=16) {
            return@synchronized InjectionResult.Failure(InjectionErrorCode.INVALID_ARGUMENT,"Duplicate pointer ID or Android 16-contact limit reached")
        }
        val service = remote?.takeIf { readyForInput } ?: return@synchronized notReady()
        if (activeTouches.isEmpty()) touchDownTime = SystemClock.uptimeMillis()
        activeTouches[pointerId] = Point(x, y)
        val ids = activeTouches.keys.toIntArray()
        val index = ids.indexOf(pointerId)
        val action = if (ids.size == 1) MotionEvent.ACTION_DOWN else {
            MotionEvent.ACTION_POINTER_DOWN or (index shl MotionEvent.ACTION_POINTER_INDEX_SHIFT)
        }
        val result = emitTouchState(service, action, SystemClock.uptimeMillis())
        if (result !is InjectionResult.Success) {
            activeTouches.remove(pointerId)
            if (activeTouches.isEmpty()) touchDownTime = 0L
        }
        result
    }

    override fun moveTouch(pointerId: Int, x: Float, y: Float): InjectionResult = synchronized(touchLock) {
        validateTap(x, y)?.let { return@synchronized it }
        val service = remote?.takeIf { readyForInput } ?: return@synchronized notReady()
        val point = activeTouches[pointerId] ?: return@synchronized InjectionResult.Failure(
            InjectionErrorCode.INVALID_ARGUMENT,
            "pointerId=$pointerId is not active"
        )
        point.x = x
        point.y = y
        emitTouchState(service, MotionEvent.ACTION_MOVE, SystemClock.uptimeMillis())
    }

    override fun endTouch(pointerId: Int): InjectionResult = synchronized(touchLock) {
        val service = remote ?: return@synchronized notReady()
        if (!activeTouches.containsKey(pointerId)) {
            return@synchronized InjectionResult.Failure(InjectionErrorCode.INVALID_ARGUMENT, "pointerId=$pointerId is not active")
        }
        val ids = activeTouches.keys.toIntArray()
        val index = ids.indexOf(pointerId)
        val action = if (ids.size == 1) MotionEvent.ACTION_UP else {
            MotionEvent.ACTION_POINTER_UP or (index shl MotionEvent.ACTION_POINTER_INDEX_SHIFT)
        }
        val result = emitTouchState(service, action, SystemClock.uptimeMillis())
        if (result is InjectionResult.Success) {
            activeTouches.remove(pointerId)
            if (activeTouches.isEmpty()) touchDownTime = 0L
        }
        result
    }

    override fun injectKeyEvent(keyCode: Int, action: Int): InjectionResult {
        validateKeyAction(action)?.let { return it }
        val service = remote?.takeIf { readyForInput } ?: return notReady()
        return try {
            val now = SystemClock.uptimeMillis()
            parseReply(service.injectKey(keyCode, action, now, now, 0, 0))
        } catch (t: Throwable) {
            InjectionResult.Failure(InjectionErrorCode.REMOTE_FAILURE, "Shizuku key injection failed: ${t.message}", t)
        }
    }

    private fun emitTouchState(service: IShizukuInputService, action: Int, eventTime: Long): InjectionResult {
        if (activeTouches.isEmpty()) {
            return InjectionResult.Failure(InjectionErrorCode.INVALID_ARGUMENT, "No active touches to emit")
        }
        val ids = activeTouches.keys.toIntArray()
        val xs = FloatArray(ids.size) { activeTouches.getValue(ids[it]).x }
        val ys = FloatArray(ids.size) { activeTouches.getValue(ids[it]).y }
        return try {
            parseReply(
                service.injectTouch(
                    action,
                    touchDownTime.takeIf { it > 0L } ?: eventTime,
                    eventTime,
                    ids.size,
                    ids,
                    xs,
                    ys
                )
            )
        } catch (t: Throwable) {
            InjectionResult.Failure(InjectionErrorCode.REMOTE_FAILURE, "Shizuku multi-touch injection failed: ${t.message}", t)
        }
    }

    private fun parseReply(reply: String): InjectionResult = if (reply == "OK") {
        InjectionResult.Success
    } else {
        InjectionResult.Failure(InjectionErrorCode.REMOTE_FAILURE, reply)
    }

    private fun notReady() = InjectionResult.Failure(InjectionErrorCode.NOT_READY, "Shizuku injector is not connected")

    fun readSurfaceLayers(): Result<String> = runCatching { (remote ?: error("Shizuku frame service is not connected")).readSurfaceLayers() }
    fun readSurfaceLatency(layer:String): Result<String> = runCatching { (remote ?: error("Shizuku frame service is not connected")).readSurfaceLatency(layer) }

    override fun cleanup(): InjectionResult {
        readyForInput = false
        connectionDetails = null
        val failures=mutableListOf<String>()
        synchronized(touchLock) {
            activeTouches.keys.toList().asReversed().forEach { id ->
                when(val release=endTouch(id)) {
                    InjectionResult.Success -> Unit
                    is InjectionResult.Failure -> failures += "Pointer $id release: ${release.message}"
                }
            }
        }
        if(failures.isNotEmpty()) {
            readyForInput = false
            return InjectionResult.Failure(InjectionErrorCode.CLEANUP_FAILURE,failures.joinToString("; "))
        }
        val localArgs=args;val localConnection=connection
        if(localArgs!=null && localConnection!=null) {
            try { runtime.unbindUserService(localArgs,localConnection,true) }
            catch(error:Throwable) { failures += "Unbind: ${error.javaClass.simpleName}: ${error.message}" }
        }
        if(failures.isNotEmpty()) return InjectionResult.Failure(InjectionErrorCode.CLEANUP_FAILURE,failures.joinToString("; "))
        synchronized(touchLock) { activeTouches.clear();touchDownTime=0L }
        synchronized(connectionLock) {
            ++connectionGeneration
            remote=null;args=null;connection=null;readyForInput=false;connectionDetails=null
        }
        return InjectionResult.Success
    }

    companion object {
        // Shizuku reuses an existing process when this implementation version is unchanged.
        const val USER_SERVICE_VERSION = 4
        private const val TAG = "NexusShizukuService"
    }
}
