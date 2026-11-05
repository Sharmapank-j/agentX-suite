package com.agentx.admin.server

import com.agentx.admin.models.ServerTelemetry
import com.agentx.admin.models.ServingMode
import kotlinx.coroutines.flow.StateFlow

/**
 * AgentX Serve Server Interface:
 * Powered by production-grade Ktor Server CIO ([KtorAgentXServer]).
 * Replaces the legacy Java ServerSocket line parser with asynchronous non-blocking
 * Ktor CIO pipelines, SSE event streaming, Zero-Trust API authentication,
 * and OpenAI-compatible endpoint compatibility.
 */
class AgentXAdminServer(
    val clientManager: ClientManager,
    val cloudGateway: CloudGeminiGateway,
    val localEngine: LocalModelServerEngine,
    val thermalManager: ThermalBatteryManager? = null,
    val tlsManager: LocalTlsManager? = null,
    val tunnelManager: TunnelManager? = null
) {
    val ktorServer = KtorAgentXServer(
        clientManager = clientManager,
        cloudGateway = cloudGateway,
        localEngine = localEngine,
        thermalManager = thermalManager,
        tlsManager = tlsManager,
        tunnelManager = tunnelManager
    )

    val telemetry: StateFlow<ServerTelemetry>
        get() = ktorServer.telemetry

    fun getServingMode(): ServingMode = ktorServer.getServingMode()

    fun setServingMode(mode: ServingMode) {
        ktorServer.setServingMode(mode)
    }

    suspend fun switchLocalModel(modelId: String): Boolean {
        return ktorServer.switchLocalModel(modelId)
    }

    suspend fun unloadLocalModel() {
        ktorServer.unloadLocalModel()
    }

    suspend fun start(port: Int = 8080, enableLan: Boolean = true): Boolean {
        return ktorServer.start(port = port, enableLan = enableLan)
    }

    suspend fun stop() {
        ktorServer.stop()
    }
}
