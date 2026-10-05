package com.inputmapper.platform.root

import android.content.Intent
import android.os.IBinder
import android.os.Process
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
            rootGuard()?.let { return@synchronized it }
            nativeLoadError?.let { return@synchronized "ERROR NATIVE_LOAD $it" }
            if (devicesCreated) return@synchronized "OK ALREADY"
            val rc = NativeUinputBridge.nativeCreate(width, height, maxSlots)
            if (rc == 0) {
                devicesCreated = true
                "OK"
            } else {
                nativeFailure("CREATE", rc)
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
                NativeUinputBridge.nativeDestroy()
                devicesCreated = false
            }
            "OK"
        }
    }

    override fun onBind(intent: Intent): IBinder = binder

    override fun onDestroy() {
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
        if (rc == 0) "OK" else nativeFailure(operation, rc)

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
        @Volatile
        private var nativeLoadError: String? = null

        init {
            if (Process.myUid() == 0) {
                try {
                    System.loadLibrary("uinput_jni")
                } catch (t: Throwable) {
                    nativeLoadError = "${t.javaClass.simpleName}:${t.message ?: "unknown"}"
                }
            }
        }
    }
}
