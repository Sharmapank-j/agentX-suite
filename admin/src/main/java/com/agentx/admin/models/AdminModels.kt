package com.agentx.admin.models

import kotlinx.serialization.Serializable

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
    val activeCloudModel: String = "gemini-2.5-flash",
    val isCloudConnected: Boolean = false,
    val lastConnectedIp: String? = null,
    val activeClientsCount: Int = 0,
    val lastError: String? = null
)
