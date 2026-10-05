package com.inputmapper.platform.root

import android.content.Intent
import android.os.IBinder
import android.os.Process
import android.util.Log
import com.topjohnwu.superuser.ipc.RootService
import java.io.File

/**
 * Root-side Binder service backed by libsu RootService.
 *
 * The service process itself runs as uid 0 and owns the JNI uinput devices.
 * The normal app process remains unprivileged.
 */
class RootInputService : RootService() {
    @Volatile
    private var devicesCreated = false

    private val binder = object : IRootInputService.Stub() {
        override fun readSurfaceLayers(): String = com.inputmapper.platform.core.SurfaceFrameProbe.layers()
        override fun readSurfaceLatency(layer: String): String = com.inputmapper.platform.core.SurfaceFrameProbe.latency(layer)

        override fun create(width: Int, height: Int, maxSlots: Int): String = synchronized(this@RootInputService) {
            rootGuard()?.let {
                Log.e(TAG, "create rejected: $it")
                return@synchronized it
            }
            nativeLoadError?.let {
                val reply = "ERROR NATIVE_LOAD $it"
                Log.e(TAG, "create rejected: $reply")
                return@synchronized reply
            }
            if (devicesCreated) {
                Log.i(TAG, "create requested while virtual devices are already active")
                return@synchronized "OK ALREADY"
            }
            Log.i(TAG, "creating virtual input devices ${width}x$height slots=$maxSlots uid=${Process.myUid()}")
            val rc = NativeUinputBridge.nativeCreate(width, height, maxSlots)
            if (rc == 0) {
                devicesCreated = true
                Log.i(TAG, "virtual input devices created successfully")
                "OK"
            } else {
                nativeFailure("CREATE", rc).also { Log.e(TAG, it) }
            }
        }

        override fun touchDown(slot: Int, trackingId: Int, x: Int, y: Int): String = synchronized(this@RootInputService) {
            if (!devicesCreated) return@synchronized "ERROR NOT_READY uinput devices are not created"
            reply("TOUCH_DOWN", NativeUinputBridge.nativeTouchDown(slot, trackingId, x, y))
        }

        override fun touchMove(slot: Int, x: Int, y: Int): String = synchronized(this@RootInputService) {
            if (!devicesCreated) return@synchronized "ERROR NOT_READY uinput devices are not created"
            reply("TOUCH_MOVE", NativeUinputBridge.nativeTouchMove(slot, x, y))
        }

        override fun touchUp(slot: Int): String = synchronized(this@RootInputService) {
            if (!devicesCreated) return@synchronized "ERROR NOT_READY uinput devices are not created"
            reply("TOUCH_UP", NativeUinputBridge.nativeTouchUp(slot))
        }

        override fun key(linuxKeyCode: Int, value: Int): String = synchronized(this@RootInputService) {
            if (!devicesCreated) return@synchronized "ERROR NOT_READY uinput devices are not created"
            reply("KEY", NativeUinputBridge.nativeKey(linuxKeyCode, value))
        }

        override fun publishState(state: String): String = synchronized(this@RootInputService) {
            rootGuard()?.let { return@synchronized it }
            if (state.length > 8192) return@synchronized "ERROR INVALID_ARGUMENT state too large"
            try {
                val dir = File("/data/adb/gamepad-pro")
                if (!dir.exists() && !dir.mkdirs()) {
                    return@synchronized "ERROR IO unable to create ${dir.path}"
                }
                val out = File(dir, "runtime-status.txt")
                out.writeText(state)
                out.setReadable(true, true)
                "OK"
            } catch (t: Throwable) {
                "ERROR IO ${t.javaClass.simpleName} ${t.message ?: "unknown"}"
            }
        }

        override fun status(): String = synchronized(this@RootInputService) {
            val uid = Process.myUid()
            val uinput = deviceAccess("/dev/uinput")
            val uhid = deviceAccess("/dev/uhid")
            val selinux = runCatching { File("/proc/self/attr/current").readText().trim() }.getOrElse {
                "unavailable:${it.javaClass.simpleName}"
            }
            val load = nativeLoadError?.let { "error:$it" } ?: "ok"
            "OK uid=$uid created=$devicesCreated native=$load uinput=$uinput uhid=$uhid selinux=$selinux"
        }

        override fun destroyDevices(): String = synchronized(this@RootInputService) {
            if (devicesCreated) {
                Log.i(TAG, "destroying virtual input devices")
                NativeUinputBridge.nativeDestroy()
                devicesCreated = false
            }
            "OK"
        }
    }

    override fun onBind(intent: Intent): IBinder {
        Log.i(TAG, "RootInputService bound uid=${Process.myUid()} pid=${Process.myPid()}")
        nativeLoadError?.let { Log.e(TAG, "JNI unavailable: $it") }
        return binder
    }

    override fun onDestroy() {
        Log.i(TAG, "RootInputService destroying; devicesCreated=$devicesCreated")
        synchronized(this) {
            if (devicesCreated) {
                NativeUinputBridge.nativeDestroy()
                devicesCreated = false
            }
        }
        super.onDestroy()
    }

    private fun rootGuard(): String? =
        if (Process.myUid() == 0) null else "ERROR NOT_ROOT uid=${Process.myUid()}"

    private fun reply(operation: String, rc: Int): String =
        if (rc == 0) "OK" else nativeFailure(operation, rc).also { Log.e(TAG, it) }

    private fun nativeFailure(operation: String, rc: Int): String =
        "ERROR NATIVE_$operation rc=$rc ${NativeUinputBridge.nativeLastError()}"

    private fun deviceAccess(path: String): String {
        val file = File(path)
        if (!file.exists()) return "missing"
        return buildString {
            append(if (file.canRead()) "r" else "-")
            append(if (file.canWrite()) "w" else "-")
        }
    }

    companion object {
        private const val TAG = "NexusRootInput"

        @Volatile
        private var nativeLoadError: String? = null

        init {
            if (Process.myUid() == 0) {
                try {
                    System.loadLibrary("uinput_jni")
                } catch (t: Throwable) {
                    nativeLoadError = "${t.javaClass.simpleName}:${t.message ?: "unknown"}"
                    Log.e(TAG, "Failed to load uinput_jni: $nativeLoadError", t)
                }
            }
        }
    }
}
