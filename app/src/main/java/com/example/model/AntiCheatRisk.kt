package com.example.model

enum class AntiCheatSeverity {
    SAFE,       // e.g. Offline games, Emulators, standard single player
    MODERATE,   // e.g. Casual multiplayer with standard heuristics (Accessibility recommended)
    HIGH_ALERT  // e.g. Strict kernel level anti-cheat (Tencent ACE, Hoyoverse, BattlEye, EasyAntiCheat)
}

data class AntiCheatRisk(
    val severity: AntiCheatSeverity,
    val title: String,
    val description: String,
    val recommendedMethod: PrivilegeMethod,
    val warningNote: String = ""
)
