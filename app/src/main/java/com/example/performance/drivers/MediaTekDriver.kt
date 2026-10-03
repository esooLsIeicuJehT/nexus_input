package com.example.performance.drivers

import android.os.Build
import com.example.model.GpuState
import com.example.performance.TuningExecutor
import java.io.File

class MediaTekDriver : GenericLinuxDriver() {
    override val driverName: String = "MediaTek Dimensity & Mali Driver"

    override fun detect(): Boolean {
        val hardware = Build.HARDWARE.lowercase()
        return hardware.contains("mt") || hardware.contains("mediatek") || File("/proc/m4u").exists()
    }

    override fun discoverGpuState(): GpuState {
        val path = "/sys/class/devfreq/13040000.mali"
        val curFreq = TuningExecutor.readSysfs("$path/cur_freq")?.toLongOrNull() ?: 600000000L

        return GpuState(
            vendor = "MediaTek / ARM",
            model = "Mali-G Series (Dimensity)",
            devfreqPath = path,
            availableFrequenciesHz = listOf(350000000L, 500000000L, 700000000L, 950000000L),
            currentFrequencyHz = curFreq,
            currentGovernor = "ged",
            availableGovernors = listOf("ged", "simple_ondemand", "performance"),
            utilizationPercent = 48
        )
    }
}
