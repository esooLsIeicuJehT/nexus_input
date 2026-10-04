package com.inputmapper.platform.core

import android.content.Context
import com.inputmapper.platform.accessibility.AccessibilityInjector
import com.inputmapper.platform.accessibility.MapperAccessibilityService
import com.inputmapper.platform.root.KernelSUInjector
import com.inputmapper.platform.root.MagiskInjector
import com.inputmapper.platform.shizuku.ShizukuInjector

class InjectorFactory(private val context: Context) {
    fun create(kind: BackendKind, screenWidth: Int, screenHeight: Int): FactoryResult {
        if (screenWidth <= 0 || screenHeight <= 0) {
            return FactoryResult.Error("Invalid screen dimensions ${screenWidth}x$screenHeight")
        }
        return when (kind) {
            BackendKind.SHIZUKU -> {
                val injector = ShizukuInjector(context)
                when (val result = injector.connect()) {
                    InjectionResult.Success -> FactoryResult.Ready(injector)
                    is InjectionResult.Failure -> FactoryResult.Error(result.message, result)
                }
            }
            BackendKind.KERNEL_SU -> {
                val injector = KernelSUInjector(context, screenWidth, screenHeight)
                when (val result = injector.connect()) {
                    InjectionResult.Success -> FactoryResult.Ready(injector)
                    is InjectionResult.Failure -> FactoryResult.Error(result.message, result)
                }
            }
            BackendKind.MAGISK -> {
                val injector = MagiskInjector(context, screenWidth, screenHeight)
                when (val result = injector.connect()) {
                    InjectionResult.Success -> FactoryResult.Ready(injector)
                    is InjectionResult.Failure -> FactoryResult.Error(result.message, result)
                }
            }
            BackendKind.APATCH -> FactoryResult.Error(
                "APatch injection backend is not implemented in Phase 0 because its device-specific su/PTY behavior has not been verified on hardware. Detection is reported separately; there is no silent fallback."
            )
            BackendKind.ACCESSIBILITY -> {
                if (MapperAccessibilityService.current == null) {
                    FactoryResult.Error("Accessibility service is not connected")
                } else {
                    FactoryResult.Ready(AccessibilityInjector())
                }
            }
        }
    }
}

sealed interface FactoryResult {
    data class Ready(val injector: InputInjector) : FactoryResult
    data class Error(val message: String, val failure: InjectionResult.Failure? = null) : FactoryResult
}
