package com.agentx.core.device

import java.io.File

data class CpuCoreInfo(
    val coreIndex: Int,
    val maxFreqKHz: Long,
    val isPerformanceCore: Boolean
)

data class CpuTopology(
    val totalCores: Int,
    val performanceCores: Int,
    val efficiencyCores: Int,
    val recommendedInferenceThreads: Int,
    val coreFrequencies: List<CpuCoreInfo>
)

object CpuTopologyInspector {
    fun inspect(): CpuTopology {
        val totalCores = Runtime.getRuntime().availableProcessors().coerceAtLeast(1)
        val coreList = mutableListOf<CpuCoreInfo>()

        var maxObservedFreq = 0L
        for (i in 0 until totalCores) {
            val freqFile = File("/sys/devices/system/cpu/cpu$i/cpufreq/cpuinfo_max_freq")
            val freq = try {
                if (freqFile.exists() && freqFile.canRead()) {
                    freqFile.readText().trim().toLongOrNull() ?: 0L
                } else {
                    0L
                }
            } catch (_: Throwable) {
                0L
            }
            if (freq > maxObservedFreq) maxObservedFreq = freq
            coreList.add(CpuCoreInfo(coreIndex = i, maxFreqKHz = freq, isPerformanceCore = false))
        }

        // Categorize cores: if maxObservedFreq > 0, cores with >= 85% of max frequency are performance cores
        val categorizedCores = if (maxObservedFreq > 0L) {
            val threshold = (maxObservedFreq * 0.85).toLong()
            coreList.map { it.copy(isPerformanceCore = it.maxFreqKHz >= threshold) }
        } else {
            // Fallback: If cannot read sysfs frequencies, assume big.LITTLE on 8-core CPUs (e.g. 6 efficiency + 2 performance)
            coreList.mapIndexed { index, core ->
                core.copy(isPerformanceCore = if (totalCores >= 8) index >= (totalCores - 2) else true)
            }
        }

        val perfCount = categorizedCores.count { it.isPerformanceCore }
        val effCount = totalCores - perfCount

        // Optimal thread recommendation:
        // Use performance cores count if between 2 and 6; otherwise clamp to thermal/battery sweet spot (typically 4 for 8-core mobile SoC)
        val recommendedThreads = when {
            perfCount in 2..6 -> perfCount
            perfCount > 6 -> 6
            totalCores >= 8 -> 4 // typical mobile sweet spot for battery/thermal balance
            totalCores in 4..7 -> (totalCores - 1).coerceAtLeast(2)
            else -> totalCores.coerceIn(1, 4)
        }

        return CpuTopology(
            totalCores = totalCores,
            performanceCores = if (perfCount > 0) perfCount else totalCores,
            efficiencyCores = effCount,
            recommendedInferenceThreads = recommendedThreads,
            coreFrequencies = categorizedCores
        )
    }
}
