package com.agentx.core.inference.model

import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
enum class ExecutionMode(val label: String, val description: String) {
    LOCAL("Local", "Inference occurs on the current device hardware."),
    REMOTE("Remote", "Inference occurs on another device (AgentX Serve, Ollama, Cloud)."),
    HYBRID("Hybrid", "Requests are routed between available providers according to explicit policies.")
}

@Serializable
data class ProviderCapabilities(
    val supportsStreaming: Boolean = true,
    val supportsTools: Boolean = false,
    val supportsVision: Boolean = false,
    val supportsEmbeddings: Boolean = false,
    val supportsCancellation: Boolean = true,
    val maxContextTokens: Int = 4096,
    val isOfflineCapable: Boolean = false
)

@Serializable
data class ProviderHealth(
    val isHealthy: Boolean = true,
    val latencyMs: Long? = null,
    val message: String? = null,
    val timestampMs: Long = System.currentTimeMillis()
)

@Serializable
data class ModelInfo(
    val id: String,
    val name: String,
    val parameters: String = "3B",
    val quantization: String = "Q4_K_M",
    val contextWindow: Int = 4096,
    val memoryFootprintMb: Long = 0L,
    val isLocal: Boolean = true,
    val providerId: String = "local_embedded",
    val description: String = "",
    val modelPath: String? = null,
    val mmprojPath: String? = null,
    val supportsVision: Boolean = false,
    val architecture: String = "llama"
)

@Serializable
data class GenerationRequest(
    val requestId: String = UUID.randomUUID().toString(),
    val prompt: String,
    val systemPrompt: String? = null,
    val temperature: Float = 0.7f,
    val topP: Float = 0.9f,
    val topK: Int = 40,
    val repetitionPenalty: Float = 1.1f,
    val seed: Int = 0,
    val maxTokens: Int = 1024,
    val stopSequences: List<String> = emptyList(),
    val tools: List<String> = emptyList(),
    val preferredModelId: String? = null,
    val executionMode: ExecutionMode = ExecutionMode.HYBRID,
    val imageBase64: String? = null,
    val imageMimeType: String? = null
)

sealed class GenerationEvent {
    data class Token(val text: String, val index: Int = 0) : GenerationEvent()
    data class Finished(
        val totalTokens: Int,
        val promptTokens: Int,
        val completionTokens: Int,
        val durationMs: Long,
        val providerId: String,
        val modelId: String,
        val tokensPerSecond: Double = 0.0,
        val ttftMs: Long = 0L
    ) : GenerationEvent()
    data class Error(
        val cause: Throwable,
        val message: String,
        val providerId: String? = null
    ) : GenerationEvent()
}

@Serializable
data class GenerationProvenance(
    val providerId: String,
    val providerName: String,
    val modelId: String,
    val executionMode: ExecutionMode,
    val durationMs: Long = 0L,
    val totalTokens: Int = 0,
    val tokensPerSecond: Double = 0.0
)
