package com.agentx.core.device

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import kotlinx.serialization.Serializable

@Serializable
enum class DevicePerformanceTier(val label: String, val description: String) {
    ENTRY("Entry Mobile Tier", "Supports ultra-lightweight models (1B–1.5B). Optimized for battery & thermal efficiency."),
    BALANCED("Balanced Daily Tier", "Supports standard mobile LLMs (3B–4B). Ideal balance of conversational intelligence and speed."),
    HIGH("High-Performance Tier", "Supports full 7B–8B flagship open-source models with advanced reasoning and code synthesis."),
    ULTRA("Flagship AI Workstation", "Supports heavy 8B+ reasoning models (e.g. DeepSeek R1 Distill) and large context windows.")
}

@Serializable
data class DeviceSpecs(
    val deviceModel: String,
    val osVersion: String,
    val totalRamGb: Float,
    val availableRamGb: Float,
    val cpuCores: Int,
    val primaryAbi: String,
    val batteryPercent: Int?,
    val performanceTier: DevicePerformanceTier
)

@Serializable
data class RecommendedModel(
    val id: String,
    val name: String,
    val parameterSize: String,
    val quantization: String,
    val downloadSizeMb: Long,
    val requiredRamGb: Float,
    val matchScorePercent: Int,
    val isBestFit: Boolean,
    val speedRating: String,
    val capabilitySummary: String,
    val huggingFaceUrl: String
)

object DeviceHardwareInspector {

    fun inspectDevice(context: Context): DeviceSpecs {
        val actManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        actManager?.getMemoryInfo(memInfo)

        val totalRamBytes = memInfo.totalMem
        val availRamBytes = memInfo.availMem
        val totalRamGb = totalRamBytes / (1024f * 1024f * 1024f)
        val availRamGb = availRamBytes / (1024f * 1024f * 1024f)

        val cores = Runtime.getRuntime().availableProcessors()
        val model = "${Build.MANUFACTURER.replaceFirstChar { it.uppercase() }} ${Build.MODEL}"
        val os = "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})"
        val abi = Build.SUPPORTED_ABIS.firstOrNull() ?: "arm64-v8a"

        val batteryIntent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val batteryLevel = batteryIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val batteryScale = batteryIntent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val batteryPct = if (batteryLevel >= 0 && batteryScale > 0) {
            (batteryLevel * 100) / batteryScale
        } else null

        val tier = when {
            totalRamGb >= 11.5f -> DevicePerformanceTier.ULTRA
            totalRamGb >= 6.5f -> DevicePerformanceTier.HIGH
            totalRamGb >= 3.8f -> DevicePerformanceTier.BALANCED
            else -> DevicePerformanceTier.ENTRY
        }

        return DeviceSpecs(
            deviceModel = model,
            osVersion = os,
            totalRamGb = (Math.round(totalRamGb * 10) / 10f),
            availableRamGb = (Math.round(availRamGb * 10) / 10f),
            cpuCores = cores,
            primaryAbi = abi,
            batteryPercent = batteryPct,
            performanceTier = tier
        )
    }

    fun getRecommendedModels(specs: DeviceSpecs): List<RecommendedModel> {
        val totalRam = specs.totalRamGb
        val availRam = specs.availableRamGb

        val catalog = listOf(
            RecommendedModel(
                id = "llama-3.2-1b",
                name = "Llama 3.2 1B Instruct",
                parameterSize = "1.23B",
                quantization = "Q4_K_M",
                downloadSizeMb = 750,
                requiredRamGb = 1.4f,
                matchScorePercent = if (totalRam < 4f) 99 else 85,
                isBestFit = totalRam < 3.8f,
                speedRating = "⚡⚡⚡ Ultra Fast (~32 t/s)",
                capabilitySummary = "Instant chat responses, quick summarization, email drafts, lightweight memory footprint.",
                huggingFaceUrl = "https://huggingface.co/bartowski/Llama-3.2-1B-Instruct-GGUF/resolve/main/Llama-3.2-1B-Instruct-Q4_K_M.gguf"
            ),
            RecommendedModel(
                id = "llama-3.2-3b",
                name = "Llama 3.2 3B Instruct",
                parameterSize = "3.21B",
                quantization = "Q4_K_M",
                downloadSizeMb = 2020,
                requiredRamGb = 2.8f,
                matchScorePercent = if (totalRam in 3.8f..6.5f) 98 else if (totalRam > 6.5f) 90 else 75,
                isBestFit = totalRam in 3.8f..6.5f,
                speedRating = "⚡⚡ Fast (~20 t/s)",
                capabilitySummary = "State-of-the-art mobile assistant. Fluent natural language, general reasoning, multilingual.",
                huggingFaceUrl = "https://huggingface.co/bartowski/Llama-3.2-3B-Instruct-GGUF/resolve/main/Llama-3.2-3B-Instruct-Q4_K_M.gguf"
            ),
            RecommendedModel(
                id = "qwen-2.5-3b",
                name = "Qwen 2.5 3B Instruct",
                parameterSize = "3.09B",
                quantization = "Q4_K_M",
                downloadSizeMb = 1950,
                requiredRamGb = 2.7f,
                matchScorePercent = if (totalRam in 3.8f..6.5f) 96 else 88,
                isBestFit = false,
                speedRating = "⚡⚡ Fast (~19 t/s)",
                capabilitySummary = "Exceptional code synthesis, math problem solving, structured JSON tool execution.",
                huggingFaceUrl = "https://huggingface.co/Qwen/Qwen2.5-3B-Instruct-GGUF/resolve/main/qwen2.5-3b-instruct-q4_k_m.gguf"
            ),
            RecommendedModel(
                id = "phi-3.5-mini",
                name = "Phi-3.5 Mini 3.8B",
                parameterSize = "3.82B",
                quantization = "Q4_K_M",
                downloadSizeMb = 2390,
                requiredRamGb = 3.2f,
                matchScorePercent = if (totalRam in 4.5f..7.5f) 94 else 82,
                isBestFit = false,
                speedRating = "⚡⚡ Moderate-Fast (~16 t/s)",
                capabilitySummary = "High-density logical reasoning, academic STEM understanding, multi-step problem solving.",
                huggingFaceUrl = "https://huggingface.co/bartowski/Phi-3.5-mini-instruct-GGUF/resolve/main/Phi-3.5-mini-instruct-Q4_K_M.gguf"
            ),
            RecommendedModel(
                id = "qwen-2.5-7b",
                name = "Qwen 2.5 7B Instruct",
                parameterSize = "7.61B",
                quantization = "Q4_K_M",
                downloadSizeMb = 4680,
                requiredRamGb = 5.6f,
                matchScorePercent = if (totalRam in 6.5f..11.5f) 97 else if (totalRam >= 11.5f) 92 else 45,
                isBestFit = totalRam in 6.5f..11.5f,
                speedRating = "⚡ Full Precision (~9-12 t/s)",
                capabilitySummary = "Flagship-grade multi-agent intelligence, software engineering, complex research analysis.",
                huggingFaceUrl = "https://huggingface.co/Qwen/Qwen2.5-7B-Instruct-GGUF/resolve/main/qwen2.5-7b-instruct-q4_k_m.gguf"
            ),
            RecommendedModel(
                id = "deepseek-r1-distill-qwen-7b",
                name = "DeepSeek R1 Distill Qwen 7B",
                parameterSize = "7.61B",
                quantization = "Q4_K_M",
                downloadSizeMb = 4680,
                requiredRamGb = 5.8f,
                matchScorePercent = if (totalRam >= 7.5f) 98 else 40,
                isBestFit = totalRam >= 11.5f,
                speedRating = "⚡ Deep Chain-of-Thought (~8-11 t/s)",
                capabilitySummary = "Advanced chain-of-thought reasoning, mathematical proofs, algorithm design, self-reflection.",
                huggingFaceUrl = "https://huggingface.co/bartowski/DeepSeek-R1-Distill-Qwen-7B-GGUF/resolve/main/DeepSeek-R1-Distill-Qwen-7B-Q4_K_M.gguf"
            )
        )

        return catalog.sortedByDescending { it.matchScorePercent }
    }
}
