package com.example.performance.drivers

import android.os.Build
import com.example.model.GpuState
import com.example.performance.TuningExecutor
import java.io.File

class ExynosDriver : GenericLinuxDriver() {
    override val driverName: String = "Samsung Exynos Hardware Driver"

    override fun detect(): Boolean {
        val hardware = Build.HARDWARE.lowercase()
        val board = Build.BOARD.lowercase()
        return hardware.contains("exynos") || board.contains("universal")
    }

    override fun discoverGpuState(): GpuState {
        val path = "/sys/class/devfreq/17000000.gpu"
        val curFreq = TuningExecutor.readSysfs("$path/cur_freq")?.toLongOrNull() ?: 580000000L

        return GpuState(
            vendor = "Samsung / Xclipse",
            model = "Xclipse / Mali-G Series (Exynos)",
            devfreqPath = path,
            availableFrequenciesHz = listOf(280000000L, 450000000L, 620000000L, 880000000L),
            currentFrequencyHz = curFreq,
            currentGovernor = "simple_ondemand",
            utilizationPercent = 50
        )
    }
}
