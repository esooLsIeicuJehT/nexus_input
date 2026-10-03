package com.example.performance.drivers

import com.example.model.CpuCluster
import com.example.model.DeviceCapabilities
import com.example.model.GpuState
import com.example.model.HardwareTelemetry
import com.example.model.MemoryState
import com.example.model.PerformanceMode
import com.example.model.ThermalState
import com.example.model.TuningVerificationResult

data class HardwareState(
    val cpuClusters: List<CpuCluster> = emptyList(),
    val gpu: GpuState = GpuState(),
    val memory: MemoryState = MemoryState(),
    val thermal: ThermalState = ThermalState(),
    val currentMode: PerformanceMode = PerformanceMode.BALANCED
)

data class FrequencyTable(
    val cpuClusterFrequencies: Map<Int, List<Long>> = emptyMap(),
    val gpuFrequenciesHz: List<Long> = emptyList(),
    val cpuAvailableGovernors: List<String> = emptyList(),
    val gpuAvailableGovernors: List<String> = emptyList()
)

interface PerformanceDriver {
    val driverName: String
    fun detect(): Boolean
    fun getCapabilities(): DeviceCapabilities
    fun readState(): HardwareState
    fun getFrequencyTable(): FrequencyTable
    fun applyProfile(mode: PerformanceMode, customParameters: Map<String, String> = emptyMap()): List<TuningVerificationResult>
    fun restoreStock(): Boolean
    fun getTelemetry(): HardwareTelemetry
}
