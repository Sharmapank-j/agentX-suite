package com.agentx.core.device

import android.content.Context
import android.os.Build
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/**
 * Semantic tactile haptic types tailored for AI interactions.
 */
enum class HapticFeedbackType {
    CLICK,           // Standard crisp tap for buttons, menus & navigations
    LIGHT_TICK,      // Micro-tick for chip selection, tab switches, and quick prompts
    SUCCESS,         // Pleasant double tactile confirmation (message sent, photo attached, model loaded)
    STREAMING_TOKEN, // Ultra-short micro-tick on token streaming (throttled to avoid motor buzzing)
    WARNING          // Alert pulse (stop generation, delete message, connection error)
}

/**
 * Resilient, high-performance haptics manager for AgentX.
 * Automatically checks the user's haptic feedback preference,
 * leverages modern VibratorManager (API 31+) & VibrationEffect (API 26+),
 * and safely suppresses exceptions on OEM hardware.
 */
object HapticFeedbackHelper {

    @Volatile
    var isEnabled: Boolean = true

    private var lastStreamingPulseTime = 0L
    private const val STREAMING_PULSE_INTERVAL_MS = 120L // Throttled for smooth neural tactile hum

    fun performHaptic(context: Context?, type: HapticFeedbackType = HapticFeedbackType.CLICK) {
        if (!isEnabled || context == null) return

        if (type == HapticFeedbackType.STREAMING_TOKEN) {
            val now = SystemClock.uptimeMillis()
            if (now - lastStreamingPulseTime < STREAMING_PULSE_INTERVAL_MS) {
                return
            }
            lastStreamingPulseTime = now
        }

        try {
            val vibrator = getVibrator(context) ?: return
            if (!vibrator.hasVibrator()) return

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val effect = when (type) {
                    HapticFeedbackType.CLICK -> VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK)
                    HapticFeedbackType.LIGHT_TICK -> VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK)
                    HapticFeedbackType.SUCCESS -> {
                        // Double subtle crisp pulse
                        val timings = longArrayOf(0, 15, 50, 20)
                        val amplitudes = intArrayOf(0, 160, 0, 220)
                        VibrationEffect.createWaveform(timings, amplitudes, -1)
                    }
                    HapticFeedbackType.STREAMING_TOKEN -> {
                        // Ultra-short gentle micro-tick
                        VibrationEffect.createOneShot(12, 90)
                    }
                    HapticFeedbackType.WARNING -> {
                        VibrationEffect.createPredefined(VibrationEffect.EFFECT_DOUBLE_CLICK)
                    }
                }
                vibrator.vibrate(effect)
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val duration = when (type) {
                    HapticFeedbackType.CLICK -> 20L
                    HapticFeedbackType.LIGHT_TICK -> 10L
                    HapticFeedbackType.SUCCESS -> 35L
                    HapticFeedbackType.STREAMING_TOKEN -> 12L
                    HapticFeedbackType.WARNING -> 60L
                }
                val amplitude = when (type) {
                    HapticFeedbackType.LIGHT_TICK -> 80
                    HapticFeedbackType.STREAMING_TOKEN -> 70
                    HapticFeedbackType.WARNING -> 255
                    else -> 180
                }
                vibrator.vibrate(VibrationEffect.createOneShot(duration, amplitude))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(20L)
            }
        } catch (_: Throwable) {
            // Safe fallback: never crash if OEM hardware or custom ROM lacks tactile support
        }
    }

    private fun getVibrator(context: Context): Vibrator? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val manager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            manager?.defaultVibrator ?: context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }
}
