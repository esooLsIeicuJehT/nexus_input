package com.inputmapper.platform.core

enum class BackendKind {
    SHIZUKU,
    MAGISK,
    KERNEL_SU,
    APATCH,
    ACCESSIBILITY
}

data class BackendAvailability(
    val kind: BackendKind,
    val state: AvailabilityState,
    val versionName: String? = null,
    val versionCode: Long? = null,
    val details: String
)

enum class AvailabilityState {
    AVAILABLE,
    INSTALLED_NOT_RUNNING,
    PERMISSION_REQUIRED,
    DENIED,
    NOT_FOUND,
    UNSUPPORTED,
    ERROR
}
