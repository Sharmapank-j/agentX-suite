package com.agentx.core.device

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import kotlinx.serialization.Serializable
import java.io.File

@Serializable
data class MemorySnapshot(
    val totalRamMb: Long = 0L,
    val availableRamMb: Long = 0L,
    val usedRamMb: Long = 0L,
    val processRssMb: Long = 0L
)

@Serializable
data class ThermalSnapshot(
    val temperatureCelsius: Float = 34.0f,
    val isCharging: Boolean = false,
    val batteryPct: Int = 100,
    val isThrottled: Boolean = false,
    val statusLabel: String = "Optimal"
)

@Serializable
data class InferencePerformanceMetrics(
    val ttftMs: Long = 0L,
    val tokensPerSec: Double = 0.0,
    val totalTokens: Int = 0,
    val durationMs: Long = 0L,
    val memoryUsedMb: Long = 0L,
    val providerId: String = "local_embedded",
    val modelId: String = ""
)

@Serializable
data class BenchmarkResult(
    val modelId: String,
    val modelName: String,
    val ttftMs: Long,
    val evalTokensPerSec: Double,
    val promptTokensPerSec: Double,
    val peakMemoryMb: Long,
    val deviceModel: String,
    val timestamp: Long = System.currentTimeMillis()
)

object HardwareProfiler {

    fun getMemorySnapshot(): MemorySnapshot {
        var totalKb = 0L
        var availKb = 0L
        try {
            val meminfo = File("/proc/meminfo")
            if (meminfo.exists() && meminfo.canRead()) {
                meminfo.forEachLine { line ->
                    if (line.startsWith("MemTotal:")) {
                        totalKb = line.substringAfter(":").trim().substringBefore(" ").trim().toLongOrNull() ?: 0L
                    } else if (line.startsWith("MemAvailable:")) {
                        availKb = line.substringAfter(":").trim().substringBefore(" ").trim().toLongOrNull() ?: 0L
                    }
                }
            }
        } catch (_: Throwable) {}

        val totalMb = totalKb / 1024L
        val availMb = availKb / 1024L
        val usedMb = if (totalMb > availMb) totalMb - availMb else 0L

        // Process Resident Set Size (RSS)
        var rssPages = 0L
        try {
            val statm = File("/proc/self/statm")
            if (statm.exists() && statm.canRead()) {
                val parts = statm.readText().trim().split("\\s+".toRegex())
                if (parts.size >= 2) {
                    rssPages = parts[1].toLongOrNull() ?: 0L
                }
            }
        } catch (_: Throwable) {}

        val rssMb = (rssPages * 4096L) / (1024L * 1024L)

        return MemorySnapshot(
            totalRamMb = totalMb,
            availableRamMb = availMb,
            usedRamMb = usedMb,
            processRssMb = rssMb
        )
    }

    fun getThermalSnapshot(context: Context?): ThermalSnapshot {
        if (context == null) return ThermalSnapshot()
        return try {
            val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            val rawTemp = intent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 340) ?: 340
            val tempC = rawTemp / 10.0f
            val plugged = intent?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0
            val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
            val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
            val pct = if (level >= 0 && scale > 0) (level * 100) / scale else 100

            val isThrottled = tempC >= 42.0f
            val statusLabel = when {
                tempC < 38.0f -> "Optimal (<38°C)"
                tempC < 42.0f -> "Warm (38-42°C)"
                tempC < 47.0f -> "Throttled (42-47°C)"
                else -> "Critical (>47°C)"
            }

            ThermalSnapshot(
                temperatureCelsius = tempC,
                isCharging = plugged != 0,
                batteryPct = pct,
                isThrottled = isThrottled,
                statusLabel = statusLabel
            )
        } catch (_: Throwable) {
            ThermalSnapshot()
        }
    }
}
