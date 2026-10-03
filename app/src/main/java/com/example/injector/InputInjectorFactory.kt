package com.example.injector

import com.example.model.PrivilegeMethod

object InputInjectorFactory {

    fun createInjector(method: PrivilegeMethod): InputInjector {
        return when (method) {
            PrivilegeMethod.SHIZUKU -> ShizukuInjector()
            PrivilegeMethod.MAGISK -> MagiskInjector()
            PrivilegeMethod.KERNELSU -> KernelSUInjector()
            PrivilegeMethod.APATCH -> APatchInjector()
            PrivilegeMethod.ACCESSIBILITY -> AccessibilityInjector()
        }
    }
}
