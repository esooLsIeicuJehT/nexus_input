package com.inputmapper.platform.shizuku

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.IBinder
import android.os.SystemClock
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

class ShizukuInjector(
    private val context: Context,
    private val runtime: ShizukuRuntime = RealShizukuRuntime
) : InputInjector {
    override val backendName: String = "Shizuku"

    @Volatile private var remote: IShizukuInputService? = null
    private var connection: ServiceConnection? = null
    private var args: Shizuku.UserServiceArgs? = null

    private data class Point(var x: Float, var y: Float)
    private val touchLock = Any()
    private val activeTouches = LinkedHashMap<Int, Point>()
    private var touchDownTime = 0L

    fun connect(timeoutMillis: Long = 5_000): InjectionResult {
        if (!runtime.pingBinder()) {
            return InjectionResult.Failure(InjectionErrorCode.BACKEND_UNAVAILABLE, "Shizuku is not running")
        }
        if (runtime.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
            return InjectionResult.Failure(InjectionErrorCode.PERMISSION_DENIED, "Shizuku permission has not been granted to this app")
        }
        remote?.let { return InjectionResult.Success }

        val latch = CountDownLatch(1)
        val localArgs = Shizuku.UserServiceArgs(
            ComponentName(context.packageName, ShizukuInputUserService::class.java.name)
        )
            .processNameSuffix("mapper_input")
            .tag("input-injector-v2")
            .version(3)
            .daemon(false)

        val localConnection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
                remote = IShizukuInputService.Stub.asInterface(service)
                latch.countDown()
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                remote = null
            }
        }

        return try {
            args = localArgs
            connection = localConnection
            runtime.bindUserService(localArgs, localConnection)
            if (!latch.await(timeoutMillis, TimeUnit.MILLISECONDS)) {
                remote = null
                InjectionResult.Failure(InjectionErrorCode.REMOTE_FAILURE, "Timed out binding Shizuku UserService")
            } else {
                val test = remote?.selfTest() ?: "ERROR service binder missing after connection"
                val uid = Regex("""\buid=(\d+)""").find(test)?.groupValues?.getOrNull(1)?.toIntOrNull()
                if (test.startsWith("OK ") && (uid == 0 || uid == 2000)) {
                    InjectionResult.Success
                } else {
                    InjectionResult.Failure(
                        InjectionErrorCode.REMOTE_FAILURE,
                        "Shizuku UserService did not confirm a privileged root/shell UID (expected 0 or 2000): $test"
                    )
                }
            }
        } catch (t: Throwable) {
            remote = null
            InjectionResult.Failure(InjectionErrorCode.REMOTE_FAILURE, "Unable to bind Shizuku UserService: ${t.message}", t)
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
        val service = remote ?: return@synchronized notReady()
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
        val service = remote ?: return@synchronized notReady()
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
        val service = remote ?: return notReady()
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
        val failures=mutableListOf<String>()
        synchronized(touchLock) {
            activeTouches.keys.toList().asReversed().forEach { id ->
                when(val release=endTouch(id)) {
                    InjectionResult.Success -> Unit
                    is InjectionResult.Failure -> failures += "Pointer $id release: ${release.message}"
                }
            }
        }
        val localArgs=args;val localConnection=connection
        if(localArgs!=null && localConnection!=null) {
            try { runtime.unbindUserService(localArgs,localConnection,true) }
            catch(error:Throwable) { failures += "Unbind: ${error.javaClass.simpleName}: ${error.message}" }
        }
        if(failures.isNotEmpty()) return InjectionResult.Failure(InjectionErrorCode.CLEANUP_FAILURE,failures.joinToString("; "))
        synchronized(touchLock) { activeTouches.clear();touchDownTime=0L }
        remote=null;args=null;connection=null
        return InjectionResult.Success
    }
}
