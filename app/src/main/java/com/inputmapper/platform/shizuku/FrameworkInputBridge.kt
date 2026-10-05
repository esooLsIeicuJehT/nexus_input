package com.inputmapper.platform.shizuku

import android.os.Build
import android.view.InputEvent
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method

/** Framework reflection for the shell/root UserService, which has no Application context. */
internal class FrameworkInputBridge private constructor(
    private val target: Any,
    private val injectMethod: Method,
    val name: String
) {
    fun inject(event: InputEvent): Boolean {
        // AOSP NONE (0) assumes success before dispatch. WAIT_FOR_RESULT observes rejection,
        // permission failure and timeout; acceptance still does not prove game-visible behavior.
        val result = frameworkCall { injectMethod.invoke(target, event, WAIT_FOR_RESULT) }
        return result as? Boolean
            ?: error("$name.injectInputEvent returned ${result?.javaClass?.name ?: "null"}, expected Boolean")
    }

    companion object {
        const val WAIT_FOR_RESULT = 1

        fun classNameForSdk(sdk: Int): String = if (sdk >= 34) {
            "android.hardware.input.InputManagerGlobal"
        } else {
            "android.hardware.input.InputManager"
        }

        fun resolve(
            sdk: Int = Build.VERSION.SDK_INT,
            loadClass: (String) -> Class<*> = { Class.forName(it) }
        ): FrameworkInputBridge {
            val className = classNameForSdk(sdk)
            val type = loadClass(className)
            val getter = type.getDeclaredMethod("getInstance").apply { isAccessible = true }
            val target = frameworkCall { getter.invoke(null) }
                ?: error("$className.getInstance returned null")
            val inject = type.getDeclaredMethod(
                "injectInputEvent", InputEvent::class.java, Int::class.javaPrimitiveType
            ).apply { isAccessible = true }
            return FrameworkInputBridge(target, inject, className.substringAfterLast('.'))
        }
    }
}

private fun <T> frameworkCall(block: () -> T): T = try {
    block()
} catch (error: InvocationTargetException) {
    // Preserve SecurityException and the framework's actual rejection reason across AIDL.
    throw error.targetException
}
