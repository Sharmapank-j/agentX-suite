package com.agentx.core.protocol.asp

import kotlinx.serialization.Serializable
import java.util.UUID

/**
 * AgentX Serve Protocol (ASP v1.0) Specification
 * Defines formal data contracts for device-to-device AI serving,
 * cluster discovery, hardware telemetry, model catalogs, and load routing.
 */

@Serializable
enum class AspComputeBackend {
    CPU_NEON,
    CPU_AVX2,
    VULKAN,
    OPENCL,
    NPU,
    GENERIC_CPU
}

@Serializable
data class AspModelDescriptor(
    val modelId: String,
    val displayName: String,
    val format: String = "GGUF",
    val parameterSize: String, // e.g. "0.5B", "1.5B", "7B"
    val quantization: String,  // e.g. "Q4_K_M", "Q5_K_M"
    val contextLength: Int = 2048,
    val isLoaded: Boolean = false,
    val memoryUsageMb: Long = 0L,
    val tokensPerSecond: Float = 0.0f
)

@Serializable
data class AspDeviceProfile(
    val nodeId: String = UUID.randomUUID().toString(),
    val nodeName: String,
    val protocolVersion: String = "1.0.0",
    val platform: String = "Android",
    val osVersion: String,
    val totalRamMb: Long,
    val availableRamMb: Long,
    val computeBackend: AspComputeBackend = AspComputeBackend.CPU_NEON,
    val batteryPercent: Int = 100,
    val isCharging: Boolean = true,
    val isThermalThrottling: Boolean = false,
    val activeEndpoints: List<String> = emptyList(),
    val availableModels: List<AspModelDescriptor> = emptyList()
)

@Serializable
data class AspInferenceMessage(
    val role: String, // "system", "user", "assistant"
    val content: String
)

@Serializable
data class AspInferenceRequest(
    val requestId: String = UUID.randomUUID().toString(),
    val modelId: String,
    val messages: List<AspInferenceMessage>,
    val temperature: Float = 0.7f,
    val topP: Float = 0.9f,
    val maxTokens: Int = 1024,
    val stream: Boolean = true,
    val clientNodeId: String = ""
)

@Serializable
data class AspChunkEvent(
    val requestId: String,
    val chunkIndex: Int,
    val deltaText: String,
    val isFinished: Boolean = false,
    val finishReason: String? = null,
    val evalTokensPerSec: Float = 0.0f
)

/**
 * Intelligent Multi-Node Selector & Load Balancer.
 * Chooses the optimal device node in the local network cluster based on:
 * 1. Model availability & loading status.
 * 2. Thermal throttling and battery constraints.
 * 3. Available RAM budget.
 * 4. Latency / tokens per second throughput.
 */
class AspNodeSelector {

    sealed class NodeSelectionResult {
        data class Selected(val profile: AspDeviceProfile, val targetEndpoint: String, val score: Float) : NodeSelectionResult()
        data class NoSuitableNode(val reason: String) : NodeSelectionResult()
    }

    fun selectOptimalNode(
        nodes: List<AspDeviceProfile>,
        requestedModelId: String?,
        estimatedRamRequiredMb: Long = 1024L
    ): NodeSelectionResult {
        if (nodes.isEmpty()) {
            return NodeSelectionResult.NoSuitableNode("No active AgentX Serve nodes discovered on the local network.")
        }

        val candidates = nodes.mapNotNull { node ->
            val score = scoreNode(node, requestedModelId, estimatedRamRequiredMb)
            if (score > 0f) Pair(node, score) else null
        }

        if (candidates.isEmpty()) {
            return NodeSelectionResult.NoSuitableNode(
                "Discovered ${nodes.size} node(s), but none meet battery, memory, or thermal requirements for model '$requestedModelId'."
            )
        }

        val best = candidates.maxByOrNull { it.second }!!
        val primaryEndpoint = best.first.activeEndpoints.firstOrNull()
            ?: "http://127.0.0.1:8080"

        return NodeSelectionResult.Selected(best.first, primaryEndpoint, best.second)
    }

    private fun scoreNode(
        node: AspDeviceProfile,
        requestedModelId: String?,
        estimatedRamRequiredMb: Long
    ): Float {
        // Hard constraint 1: Low battery without charging is disqualified for heavy serving
        if (!node.isCharging && node.batteryPercent < 15) return 0f

        // Hard constraint 2: Must have enough available RAM
        if (node.availableRamMb < estimatedRamRequiredMb) return 0f

        var score = 50f

        // Prefer nodes that already have the model loaded in memory
        val modelMatch = node.availableModels.firstOrNull { it.modelId.equals(requestedModelId, ignoreCase = true) }
        if (modelMatch != null) {
            score += 30f
            if (modelMatch.isLoaded) score += 20f
            score += (modelMatch.tokensPerSecond * 0.5f).coerceAtMost(20f)
        } else if (requestedModelId != null && node.availableModels.isNotEmpty()) {
            // Node exists but does not carry requested model
            return 0f
        }

        // Penalize thermal throttling heavily
        if (node.isThermalThrottling) {
            score -= 35f
        }

        // Reward charging and high battery
        if (node.isCharging) score += 10f
        score += (node.batteryPercent * 0.1f)

        // Reward compute acceleration
        when (node.computeBackend) {
            AspComputeBackend.VULKAN -> score += 15f
            AspComputeBackend.NPU -> score += 20f
            AspComputeBackend.OPENCL -> score += 12f
            AspComputeBackend.CPU_NEON -> score += 8f
            else -> score += 0f
        }

        return score.coerceAtLeast(1f)
    }
}
