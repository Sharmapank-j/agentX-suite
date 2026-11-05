package com.agentx.admin.models

import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
enum class ServingMode(val label: String, val description: String) {
    LOCAL_MODEL("Local GGUF Runtime (Unavailable)", "Local inference is unavailable until a real GGUF runtime is installed and validated."),
    CLOUD_GATEWAY("Cloud Gateway (Gemini API)", "Relays requests to the configured Gemini cloud endpoint."),
    HYBRID("Hybrid (Local Runtime Required)", "Uses a validated local runtime when available; cloud fallback requires explicit configuration.")
}

@Serializable
enum class LocalModelStatus {
    NOT_LOADED,
    LOADING,
    LOADED,
    GENERATING
}

@Serializable
enum class TunnelProvider(val displayName: String) {
    CLOUDFLARE("Cloudflare Tunnel"),
    NGROK("ngrok"),
    PINGGY("Pinggy.io"),
    CUSTOM_RELAY("Custom Reverse Proxy")
}

@Serializable
enum class TunnelStatus(val label: String) {
    STOPPED("Stopped"),
    STARTING("Connecting..."),
    CONNECTED("Active (HTTPS)"),
    ERROR("Connection Failed")
}

@Serializable
data class TunnelConfig(
    val isEnabled: Boolean = false,
    val provider: TunnelProvider = TunnelProvider.CLOUDFLARE,
    val publicUrl: String? = null,
    val authToken: String = "",
    val customDomain: String = "",
    val status: TunnelStatus = TunnelStatus.STOPPED,
    val lastError: String? = null,
    val latencyMs: Long? = null
)

@Serializable
data class CliEntry(
    val id: String = UUID.randomUUID().toString(),
    val command: String? = null,
    val output: String,
    val isError: Boolean = false,
    val timestamp: Long = System.currentTimeMillis()
)

@Serializable
data class LocalServerModel(
    val id: String,
    val name: String,
    val architecture: String,
    val parameterCount: String,
    val quantization: String,
    val contextWindow: Int = 4096,
    val memoryFootprintMb: Long = 0L,
    val description: String,
    val isLoaded: Boolean = false,
    val isServing: Boolean = false,
    val filePath: String? = null,
    val fileSizeBytes: Long = 0L,
    val isCustomStorageModel: Boolean = false
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
    val activeLocalModel: String? = null,
    val localModelStatus: LocalModelStatus = LocalModelStatus.NOT_LOADED,
    val localMemoryUsageMb: Long = 0L,
    val activeCloudModel: String = "gemini-2.5-flash",
    val isCloudConnected: Boolean = false,
    val lastConnectedIp: String? = null,
    val activeClientsCount: Int = 0,
    val lastError: String? = null,
    val tunnelUrl: String? = null,
    val isTunnelActive: Boolean = false
)
