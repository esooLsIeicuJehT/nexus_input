package com.example.model

enum class PerformanceMode(val displayName: String, val description: String) {
    ECO("Eco", "Conservative frequency scaling, reduced boost, maximum battery efficiency"),
    BATTERY_SAVER("Battery Saver", "Strict power ceiling, aggressive thermal safety, minimal background CPU"),
    BALANCED("Balanced (Recommended)", "Smooth UI, responsive scaling, standard temperatures, daily use"),
    PERFORMANCE("Performance", "Faster app response, scheduler boost, moderate thermal headroom"),
    GAMING("Gaming", "Optimized for high framerates, active GPU ramping, input priority"),
    EXTREME("Extreme / Beast Mode", "Maximum sustained clock within safe thermal limits (advanced users)"),
    CUSTOM("Custom", "User-defined governors, devfreq, and sysfs parameters")
}

data class CpuCluster(
    val id: Int,
    val name: String, // e.g., "Little Cluster (Cores 0-3)", "Big Cluster (Cores 4-6)", "Prime Core (Core 7)"
    val coreRange: String,
    val availableGovernors: List<String> = listOf("schedutil", "performance", "powersave", "conservative"),
    val currentGovernor: String = "schedutil",
    val availableFrequenciesKhz: List<Long> = emptyList(),
    val currentFrequencyKhz: Long = 0L,
    val minFrequencyKhz: Long = 0L,
    val maxFrequencyKhz: Long = 0L,
    val upRateLimitUs: Int = 1000,
    val downRateLimitUs: Int = 20000
)

data class GpuState(
    val vendor: String = "Generic / Devfreq",
    val model: String = "Adreno / Mali",
    val devfreqPath: String = "/sys/class/devfreq",
    val availableFrequenciesHz: List<Long> = emptyList(),
    val currentFrequencyHz: Long = 0L,
    val minFrequencyHz: Long = 0L,
    val maxFrequencyHz: Long = 0L,
    val currentGovernor: String = "msm-adreno-tz",
    val availableGovernors: List<String> = listOf("msm-adreno-tz", "simple_ondemand", "performance"),
    val utilizationPercent: Int = 0,
    val isFrequencyControlSupported: Boolean = true,
    val isGovernorControlSupported: Boolean = true
)

data class MemoryState(
    val totalRamMb: Long = 8192L,
    val availableRamMb: Long = 4200L,
    val zramTotalMb: Long = 3072L,
    val zramUsedMb: Long = 850L,
    val currentZramAlgorithm: String = "lz4",
    val supportedZramAlgorithms: List<String> = listOf("lz4", "zstd", "lzo"),
    val swappiness: Int = 60,
    val dirtyRatio: Int = 20,
    val dirtyBackgroundRatio: Int = 10,
    val psiMemorySomeAvg10: Float = 0.05f
)

data class ThermalState(
    val batteryTempC: Float = 32.5f,
    val cpuTempC: Float = 38.0f,
    val gpuTempC: Float = 36.5f,
    val skinTempC: Float = 31.0f,
    val activeThermalZoneCount: Int = 8,
    val softThermalLimitC: Float = 42.0f,
    val aggressiveThermalLimitC: Float = 45.0f,
    val emergencySafetyLimitC: Float = 50.0f,
    val isEmergencyThrottled: Boolean = false,
    val interventionCount: Int = 0
)

data class DeviceCapabilities(
    val manufacturer: String = "Google",
    val model: String = "Pixel",
    val soc: String = "Tensor / Snapdragon",
    val androidVersion: Int = 14,
    val kernelVersion: String = "Linux 6.1-android",
    val rootFramework: String = "KernelSU Next",
    val cpuFrequencyControl: Boolean = true,
    val cpuGovernorControl: Boolean = true,
    val gpuFrequencyControl: Boolean = true,
    val gpuGovernorControl: Boolean = true,
    val zramControl: Boolean = true,
    val thermalMonitoring: Boolean = true,
    val displayRefreshControl: Boolean = true,
    val supportedRefreshRates: List<Int> = listOf(60, 90, 120)
)

data class HardwareTelemetry(
    val timestamp: Long = System.currentTimeMillis(),
    val fps: Float = 60.0f,
    val onePercentLowFps: Float = 52.0f,
    val frameTimeMs: Float = 16.6f,
    val cpuUtilizationTotal: Int = 38,
    val gpuUtilizationTotal: Int = 45,
    val batteryTempC: Float = 34.2f,
    val batteryPercent: Int = 88,
    val isCharging: Boolean = false,
    val activeProfileName: String = "Gaming"
)

data class TuningVerificationResult(
    val parameterName: String,
    val requestedValue: String,
    val readBackValue: String,
    val isSuccess: Boolean,
    val failureReason: String? = null // e.g. "Unsupported", "Permission denied", "Vendor controlled"
)

data class GameSessionReport(
    val gameTitle: String,
    val durationMinutes: Int,
    val averageFps: Float,
    val onePercentLowFps: Float,
    val peakTemperatureC: Float,
    val batteryStartPercent: Int,
    val batteryEndPercent: Int,
    val activeProfile: String,
    val thermalInterventionsCount: Int,
    val aiRecommendation: String
)
