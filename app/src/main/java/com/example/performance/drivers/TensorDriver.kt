package com.example.performance.drivers

import android.os.Build
import com.example.model.GpuState
import com.example.performance.TuningExecutor
import java.io.File

class TensorDriver : GenericLinuxDriver() {
    override val driverName: String = "Google Tensor Hardware Driver"

    override fun detect(): Boolean {
        val hardware = (Build.HARDWARE + " " + Build.SOC_MODEL).lowercase()
        return hardware.contains("tensor") || hardware.contains("gs101") || hardware.contains("gs201") || hardware.contains("zuma")
    }

    override fun discoverGpuState(): GpuState {
        val path = "/sys/class/devfreq/1c500000.mali"
        val curFreq = TuningExecutor.readSysfs("$path/cur_freq")?.toLongOrNull() ?: 620000000L

        return GpuState(
            vendor = "Google / ARM",
            model = "Mali-G710 / G715 (Tensor TPU)",
            devfreqPath = path,
            availableFrequenciesHz = listOf(300000000L, 480000000L, 640000000L, 848000000L),
            currentFrequencyHz = curFreq,
            currentGovernor = "simple_ondemand",
            availableGovernors = listOf("simple_ondemand", "performance"),
            utilizationPercent = 46
        )
    }
}
