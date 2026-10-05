package com.example.model

enum class PrivilegeMethod(
    val title: String,
    val description: String,
    val requiresRoot: Boolean,
    val badgeLabel: String
) {
    SHIZUKU(
        title = "Shizuku (Wireless ADB)",
        description = "Shell input injection through the actual Shizuku UserService. Requires a running Shizuku server and granted permission.",
        requiresRoot = false,
        badgeLabel = "Recommended No-Root"
    ),
    MAGISK(
        title = "Magisk RootService /dev/uinput",
        description = "Persistent touch transport through libsu RootService and JNI /dev/uinput. Requires root authorization and uinput access.",
        requiresRoot = true,
        badgeLabel = "Deep Root"
    ),
    KERNELSU(
        title = "KernelSU RootService /dev/uinput",
        description = "Persistent touch transport through the existing libsu RootService and JNI /dev/uinput. The companion module supplies diagnostics.",
        requiresRoot = true,
        badgeLabel = "Kernel Direct"
    ),
    APATCH(
        title = "APatch — UNVERIFIED",
        description = "Unavailable until the RootService transport is tested on actual APatch hardware.",
        requiresRoot = true,
        badgeLabel = "UNVERIFIED"
    ),
    ACCESSIBILITY(
        title = "Accessibility Fallback (Non-Root)",
        description = "Android gesture dispatch for TAP mappings. Persistent HOLD, sticks and camera drag require Shizuku or root.",
        requiresRoot = false,
        badgeLabel = "Stock Compatible"
    )
}
