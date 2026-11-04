package com.agentx.admin.server

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ThermalStatus(val label: String, val badgeColorHex: Long, val extraDelayMs: Long) {
    OPTIMAL("Optimal (<38°C)", 0xFF10B981, 0L),
    MODERATE("Warm (38°C - 42°C)", 0xFFF59E0B, 15L),
    THROTTLED("Hot (42°C - 47°C) • Adaptive Cooling", 0xFFF97316, 40L),
    CRITICAL("Critical (>47°C) • Aggressive Throttling", 0xFFEF4444, 85L)
}

data class ThermalSnapshot(
    val temperatureCelsius: Float = 34.0f,
    val isCharging: Boolean = false,
    val batteryPct: Int = 100,
    val status: ThermalStatus = ThermalStatus.OPTIMAL
)

class ThermalBatteryManager(private val context: Context) {

    private val _snapshot = MutableStateFlow(readThermalSnapshot())
    val snapshot: StateFlow<ThermalSnapshot> = _snapshot.asStateFlow()

    fun refresh(): ThermalSnapshot {
        val s = readThermalSnapshot()
        _snapshot.value = s
        return s
    }

    fun getThrottleDelayMs(): Long {
        return refresh().status.extraDelayMs
    }

    private fun readThermalSnapshot(): ThermalSnapshot {
        return try {
            val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            val rawTemp = intent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 340) ?: 340
            val tempC = rawTemp / 10.0f
            val plugged = intent?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0
            val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
            val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
            val pct = if (level >= 0 && scale > 0) (level * 100) / scale else 100

            val status = when {
                tempC < 38.0f -> ThermalStatus.OPTIMAL
                tempC < 42.0f -> ThermalStatus.MODERATE
                tempC < 47.0f -> ThermalStatus.THROTTLED
                else -> ThermalStatus.CRITICAL
            }

            ThermalSnapshot(
                temperatureCelsius = tempC,
                isCharging = plugged != 0,
                batteryPct = pct,
                status = status
            )
        } catch (_: Exception) {
            ThermalSnapshot()
        }
    }
}
