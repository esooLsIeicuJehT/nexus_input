package com.example.model

enum class SafetyStatus(val displayName: String, val colorHex: Long) {
    UNKNOWN("Unknown Status", 0xFF9CA3AF),
    NO_KNOWN_ISSUES("No Known Issues Reported", 0xFF00E676),
    USE_CAUTION("Use Caution", 0xFFFFD600),
    INCOMPATIBLE_REPORTS("Reports of Incompatibility", 0xFFFF9100),
    INJECTION_DISCOURAGED("Input Injection Discouraged", 0xFFFF1744)
}

data class GameSafetyReport(
    val gamePackage: String,
    val gameTitle: String,
    val safetyStatus: SafetyStatus,
    val rootCompatibility: String = "KernelSU / APatch / Magisk supported via /dev/uinput",
    val overlayCompatibility: String = "Normal system overlay supported",
    val shizukuCompatibility: String = "Direct shell injection supported",
    val accessibilityCompatibility: String = "Android Accessibility Service supported",
    val antiCheatEngine: String = "Standard heuristic / Proprietary",
    val banRiskSummary: String = "No bans reported for standard /dev/uinput virtual controller. Avoid automated rapid-fire macros in ranked play.",
    val lastVerifiedDate: String = "2026-09-15",
    val userReportsCount: Int = 124,
    val maintainerNotes: String = "Tested with Controlyst KernelSU Module v1.0.0. Hardware /dev/uinput injection registers as an official OTG gamepad."
)
