package com.agentx.admin.models

import kotlinx.serialization.Serializable

@Serializable
enum class ServingMode(val label: String, val description: String) {
    LOCAL_MODEL("Local On-Device Engine (AirLLM)", "Serves requests completely offline using mobile layer-streaming (zero cloud cost, 100% private)."),
    CLOUD_GATEWAY("Cloud Gateway (Gemini API)", "Relays requests to Google Gemini cloud for large-scale multimodal inference."),
    HYBRID("Hybrid Auto-Fallback", "Prioritizes local on-device engine; falls back to cloud gateway when available.")
}

@Serializable
enum class LocalModelStatus {
    NOT_LOADED,
    LOADING,
    LOADED,
    GENERATING
}

@Serializable
data class LocalServerModel(
    val id: String,
    val name: String,
    val architecture: String,
    val parameterCount: String,
    val quantization: String,
    val contextWindow: Int = 4096,
    val memoryFootprintMb: Long = 196L,
    val description: String,
    val isLoaded: Boolean = false,
    val isServing: Boolean = false
)

@Serializable
data class ClientSession(
    val id: String,
    val ipAddress: String,
    val deviceName: String = "Client Device",
    val firstSeenMs: Long = System.currentTimeMillis(),
    val lastSeenMs: Long = System.currentTimeMillis(),
    val totalRequests: Long = 0L,
    val totalTokens: Long = 0L,
    val isBlocked: Boolean = false
)

@Serializable
data class ApiKeyRecord(
    val key: String,
    val clientName: String,
    val createdAtMs: Long = System.currentTimeMillis(),
    val isRevoked: Boolean = false,
    val totalRequests: Long = 0L
)

@Serializable
data class TrafficLog(
    val id: String,
    val timestampMs: Long = System.currentTimeMillis(),
    val clientIp: String,
    val method: String,
    val path: String,
    val statusCode: Int,
    val latencyMs: Long,
    val tokensEmitted: Int
)

@Serializable
data class ServerTelemetry(
    val isRunning: Boolean = false,
    val port: Int = 8080,
    val ipAddress: String = "127.0.0.1",
    val requestsServed: Long = 0L,
    val tokensEmitted: Long = 0L,
    val startedAtMs: Long? = null,
    val servingMode: ServingMode = ServingMode.LOCAL_MODEL,
    val activeLocalModel: String? = "Llama-3.2-3B-Instruct (AirLLM)",
    val localModelStatus: LocalModelStatus = LocalModelStatus.LOADED,
    val localMemoryUsageMb: Long = 196L,
    val activeCloudModel: String = "gemini-2.5-flash",
    val isCloudConnected: Boolean = false,
    val lastConnectedIp: String? = null,
    val activeClientsCount: Int = 0,
    val lastError: String? = null
)
