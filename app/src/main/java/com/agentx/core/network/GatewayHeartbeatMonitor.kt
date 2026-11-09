package com.agentx.core.network

import com.agentx.core.inference.CloudInferenceEngine
import com.agentx.core.inference.CloudModelConfig
import com.agentx.core.inference.CloudProvider
import com.agentx.core.inference.GatewayUrlNormalizer
import com.agentx.core.protocol.pairing.RendezvousService
import com.agentx.data.local.SettingsPreferencesManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.HttpURLConnection
import java.net.URL

/**
 * Continuous Health & Heartbeat Engine for AgentX Gateway connections.
 * Periodically verifies gateway responsiveness, measures round-trip latency,
 * and automatically recovers connections when IP addresses or Wi-Fi networks change.
 */
class GatewayHeartbeatMonitor(
    private val preferencesManager: SettingsPreferencesManager,
    private val networkMonitor: NetworkConnectivityMonitor,
    private val inferenceEngine: CloudInferenceEngine? = null,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO + Job())
) {
    enum class ConnectionStatus {
        OFFLINE,
        CONNECTING,
        CONNECTED,
        DEGRADED
    }

    data class GatewayHealthState(
        val status: ConnectionStatus = ConnectionStatus.OFFLINE,
        val latencyMs: Long? = null,
        val activeModel: String? = null,
        val serverName: String? = null,
        val currentEndpoint: String = "",
        val lastSuccessfulPingMs: Long = 0L,
        val consecutiveFailures: Int = 0
    )

    private val _healthState = MutableStateFlow(GatewayHealthState())
    val healthState: StateFlow<GatewayHealthState> = _healthState.asStateFlow()

    private val rendezvousService = RendezvousService()
    private var heartbeatJob: Job? = null

    init {
        startHeartbeatLoop()
        observeNetworkTransitions()
    }

    private fun observeNetworkTransitions() {
        scope.launch {
            networkMonitor.networkState.collect { netState ->
                if (netState.isConnected) {
                    // Trigger immediate probe on network switch (e.g. Wi-Fi <-> Cellular)
                    probeGatewayNow()
                } else {
                    _healthState.value = _healthState.value.copy(
                        status = ConnectionStatus.OFFLINE,
                        consecutiveFailures = _healthState.value.consecutiveFailures + 1
                    )
                }
            }
        }
    }

    fun startHeartbeatLoop() {
        heartbeatJob?.cancel()
        heartbeatJob = scope.launch {
            while (isActive) {
                probeGatewayNow()
                delay(10_000) // 10-second heartbeat interval
            }
        }
    }

    /**
     * Executes an immediate ping to the gateway, attempting auto-rendezvous recovery if unreachable.
     */
    suspend fun probeGatewayNow(): GatewayHealthState = withContext(Dispatchers.IO) {
        val config = preferencesManager.loadCloudConfig()
        if (config.provider != CloudProvider.CUSTOM_GATEWAY) {
            _healthState.value = GatewayHealthState(status = ConnectionStatus.OFFLINE)
            return@withContext _healthState.value
        }

        val baseUrl = config.customBaseUrl
        val endpoints = GatewayUrlNormalizer.normalize(baseUrl)
        val start = System.currentTimeMillis()

        var success = false
        var latency: Long? = null
        var modelName: String? = null
        var serverTitle: String? = null

        try {
            val url = URL(endpoints.healthUrl)
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                setRequestProperty("Accept", "application/json")
                val key = config.apiKeyFor(CloudProvider.CUSTOM_GATEWAY).ifBlank { "ax-master-serve-key" }
                setRequestProperty("Authorization", "Bearer $key")
                setRequestProperty("X-API-Key", key)
                connectTimeout = 3500
                readTimeout = 3500
            }

            val code = conn.responseCode
            latency = System.currentTimeMillis() - start
            if (code in 200..299) {
                success = true
                val body = conn.inputStream.bufferedReader().use { it.readText() }
                try {
                    val json = Json.parseToJsonElement(body).jsonObject
                    serverTitle = json["server"]?.jsonPrimitive?.content
                    modelName = json["activeLocalModel"]?.jsonPrimitive?.content
                } catch (_: Exception) {}
            }
            conn.disconnect()
        } catch (_: Exception) {}

        if (success) {
            val newState = GatewayHealthState(
                status = if ((latency ?: 0) > 800) ConnectionStatus.DEGRADED else ConnectionStatus.CONNECTED,
                latencyMs = latency,
                activeModel = modelName,
                serverName = serverTitle,
                currentEndpoint = baseUrl,
                lastSuccessfulPingMs = System.currentTimeMillis(),
                consecutiveFailures = 0
            )
            _healthState.value = newState
            return@withContext newState
        }

        // Connection failed: check if we can auto-recover via saved 6-digit pairing code
        val failures = _healthState.value.consecutiveFailures + 1
        val savedCode = preferencesManager.loadSavedPairingCode()

        if (failures >= 1 && !savedCode.isNullOrBlank()) {
            val resolvedPayload = rendezvousService.resolve(savedCode)
            if (resolvedPayload != null) {
                val newEndpoint = resolvedPayload.preferredEndpoint()
                if (newEndpoint.isNotBlank() && newEndpoint != baseUrl) {
                    // Update configuration with dynamically discovered endpoint
                    val updatedCfg = config.copy(
                        customBaseUrl = newEndpoint,
                        modelName = resolvedPayload.activeModel.ifBlank { config.modelName }
                    )
                    preferencesManager.saveCloudConfig(updatedCfg)
                    inferenceEngine?.config = updatedCfg

                    // Immediate retry against the updated endpoint
                    return@withContext probeGatewayNow()
                }
            }
        }

        val failedState = _healthState.value.copy(
            status = if (failures >= 3) ConnectionStatus.OFFLINE else ConnectionStatus.CONNECTING,
            consecutiveFailures = failures
        )
        _healthState.value = failedState
        failedState
    }

    fun stop() {
        heartbeatJob?.cancel()
        heartbeatJob = null
    }
}
