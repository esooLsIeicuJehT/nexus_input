package com.example.performance.drivers

import android.os.Build
import com.example.model.GpuState
import com.example.performance.TuningExecutor
import java.io.File

class QualcommDriver : GenericLinuxDriver() {
    override val driverName: String = "Qualcomm Snapdragon & Adreno Driver"

    override fun detect(): Boolean {
        val hardware = Build.HARDWARE.lowercase()
        val board = Build.BOARD.lowercase()
        return hardware.contains("qcom") || hardware.contains("qualcomm") ||
                board.contains("qcom") || File("/sys/class/kgsl/kgsl-3d0").exists()
    }

    override fun discoverGpuState(): GpuState {
        val kgslDevfreq = "/sys/class/kgsl/kgsl-3d0/devfreq"
        val path = if (File(kgslDevfreq).exists()) kgslDevfreq else "/sys/class/devfreq/1c00000.qcom,kgsl-3d0"

        val curFreq = TuningExecutor.readSysfs("$path/cur_freq")?.toLongOrNull() ?: 550000000L
        val curGov = TuningExecutor.readSysfs("$path/governor") ?: "msm-adreno-tz"

        return GpuState(
            vendor = "Qualcomm",
            model = "Adreno Series (kgsl-3d0)",
            devfreqPath = path,
            availableFrequenciesHz = listOf(305000000L, 450000000L, 600000000L, 750000000L, 900000000L),
            currentFrequencyHz = curFreq,
            currentGovernor = curGov,
            availableGovernors = listOf("msm-adreno-tz", "simple_ondemand", "performance"),
            utilizationPercent = 52
        )
    }
}
