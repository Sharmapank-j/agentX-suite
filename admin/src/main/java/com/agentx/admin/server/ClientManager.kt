package com.agentx.admin.server

import com.agentx.admin.models.ApiKeyRecord
import com.agentx.admin.models.ClientSession
import com.agentx.admin.models.TrafficLog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.security.SecureRandom
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class ClientManager {

    private val _apiKeys = MutableStateFlow<List<ApiKeyRecord>>(emptyList())
    val apiKeys: StateFlow<List<ApiKeyRecord>> = _apiKeys.asStateFlow()

    private val _clients = MutableStateFlow<List<ClientSession>>(emptyList())
    val clients: StateFlow<List<ClientSession>> = _clients.asStateFlow()

    private val _trafficLogs = MutableStateFlow<List<TrafficLog>>(emptyList())
    val trafficLogs: StateFlow<List<TrafficLog>> = _trafficLogs.asStateFlow()

    private val clientMap = ConcurrentHashMap<String, ClientSession>()
    private val keyMap = ConcurrentHashMap<String, ApiKeyRecord>()

    init {
        // Create an initial default Master Key
        val defaultKey = generateKeyString()
        val defaultRecord = ApiKeyRecord(
            key = defaultKey,
            clientName = "Default Hospital Key"
        )
        keyMap[defaultKey] = defaultRecord
        _apiKeys.value = listOf(defaultRecord)
    }

    fun createApiKey(clientName: String): ApiKeyRecord {
        val key = generateKeyString()
        val record = ApiKeyRecord(
            key = key,
            clientName = clientName.ifBlank { "Unnamed Client" }
        )
        keyMap[key] = record
        _apiKeys.value = keyMap.values.toList().sortedByDescending { it.createdAtMs }
        return record
    }

    fun revokeApiKey(key: String) {
        keyMap[key]?.let { existing ->
            val updated = existing.copy(isRevoked = true)
            keyMap[key] = updated
            _apiKeys.value = keyMap.values.toList().sortedByDescending { it.createdAtMs }
        }
    }

    fun isKeyValid(key: String?): Boolean {
        if (key.isNullOrBlank()) return false
        val record = keyMap[key] ?: return false
        return !record.isRevoked
    }

    fun recordClientActivity(ipAddress: String, tokens: Int, deviceName: String = "Client Device") {
        val existing = clientMap[ipAddress]
        val updated = if (existing != null) {
            existing.copy(
                lastSeenMs = System.currentTimeMillis(),
                totalRequests = existing.totalRequests + 1,
                totalTokens = existing.totalTokens + tokens
            )
        } else {
            ClientSession(
                id = UUID.randomUUID().toString().take(8),
                ipAddress = ipAddress,
                deviceName = deviceName,
                totalRequests = 1,
                totalTokens = tokens.toLong()
            )
        }
        clientMap[ipAddress] = updated
        _clients.value = clientMap.values.toList().sortedByDescending { it.lastSeenMs }
    }

    fun setClientBlocked(ipAddress: String, blocked: Boolean) {
        clientMap[ipAddress]?.let { existing ->
            val updated = existing.copy(isBlocked = blocked)
            clientMap[ipAddress] = updated
            _clients.value = clientMap.values.toList().sortedByDescending { it.lastSeenMs }
        }
    }

    fun isClientBlocked(ipAddress: String): Boolean {
        return clientMap[ipAddress]?.isBlocked == true
    }

    fun logTraffic(
        clientIp: String,
        method: String,
        path: String,
        statusCode: Int,
        latencyMs: Long,
        tokens: Int
    ) {
        val log = TrafficLog(
            id = UUID.randomUUID().toString().take(8),
            clientIp = clientIp,
            method = method,
            path = path,
            statusCode = statusCode,
            latencyMs = latencyMs,
            tokensEmitted = tokens
        )
        val current = _trafficLogs.value.toMutableList()
        current.add(0, log)
        if (current.size > 150) {
            _trafficLogs.value = current.take(150)
        } else {
            _trafficLogs.value = current
        }
    }

    companion object {
        private fun generateKeyString(): String {
            val random = SecureRandom()
            val chars = "abcdefghijklmnopqrstuvwxyz0123456789"
            val sb = StringBuilder("agx_adm_")
            repeat(28) {
                sb.append(chars[random.nextInt(chars.length)])
            }
            return sb.toString()
        }
    }
}
