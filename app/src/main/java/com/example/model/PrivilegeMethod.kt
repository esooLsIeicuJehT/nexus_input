package com.example.model

enum class PrivilegeMethod(
    val title: String,
    val description: String,
    val requiresRoot: Boolean,
    val badgeLabel: String
) {
    SHIZUKU(
        title = "Shizuku (Wireless ADB)",
        description = "High-performance direct shell event injection without root. Minimal latency, zero kernel modifications.",
        requiresRoot = false,
        badgeLabel = "Recommended No-Root"
    ),
    MAGISK(
        title = "Magisk Root (su /dev/input)",
        description = "Standard systemless root access. Grants direct /dev/input raw event synthesis and kernel-level process management.",
        requiresRoot = true,
        badgeLabel = "Deep Root"
    ),
    KERNELSU(
        title = "KernelSU Userspace Daemon",
        description = "Kernel-integrated root with userspace daemon socket. Ultra-low latency raw touch frame injection.",
        requiresRoot = true,
        badgeLabel = "Kernel Direct"
    ),
    APATCH(
        title = "APatch Kernel Patch",
        description = "Kernel-level patch root. Full input subsystem access with clean process cleanup on game termination.",
        requiresRoot = true,
        badgeLabel = "Kernel Patch"
    ),
    ACCESSIBILITY(
        title = "Accessibility Fallback (Non-Root)",
        description = "Standard Android AccessibilityService gesture injection. Works on any stock device without root or PC connection.",
        requiresRoot = false,
        badgeLabel = "Stock Compatible"
    )
}
