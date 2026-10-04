package com.inputmapper.platform.core

class InjectorSessionManager {
    @Volatile private var active: InputInjector? = null

    @Synchronized
    fun replace(next: InputInjector): InjectionResult {
        val previous = active
        if (previous != null) {
            val cleanup = previous.cleanup()
            if (cleanup !is InjectionResult.Success) return cleanup
        }
        active = next
        return InjectionResult.Success
    }

    fun current(): InputInjector? = active

    @Synchronized
    fun stop(): InjectionResult {
        val current = active ?: return InjectionResult.Success
        val result = current.cleanup()
        if (result is InjectionResult.Success) active = null
        return result
    }
}
