package com.agentx.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.agentx.core.discovery.DiscoveredGatewayHub
import com.agentx.core.discovery.GatewayNsdDiscovery
import com.agentx.core.agent.execution.ExecutionQueue
import com.agentx.core.agent.execution.ExecutionState
import com.agentx.core.inference.CloudModelConfig
import com.agentx.core.inference.CloudProvider
import com.agentx.core.inference.GatewayEndpoints
import com.agentx.core.inference.GatewayUrlNormalizer
import com.agentx.core.inference.HybridInferenceEngine
import com.agentx.core.inference.InferenceMode
import com.agentx.data.repository.ExecutionRepository
import com.agentx.data.repository.ToolRepository
import com.agentx.domain.models.ExecutionLog
import com.agentx.domain.models.ExecutionStatus
import com.agentx.domain.models.Tool
import com.agentx.domain.models.ToolPermission
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.HttpURLConnection
import java.net.URL

import com.agentx.core.agent.tools.mcp.HttpMcpClient
import com.agentx.core.agent.tools.mcp.McpServerDescriptor
import com.agentx.core.agent.tools.mcp.McpServerRegistry
import com.agentx.core.inference.InferenceEngine
import com.agentx.core.inference.ModelConfig
import com.agentx.core.inference.ModelLifecycleState
import com.agentx.core.inference.ModelStatus
import com.agentx.core.memory.DefaultLongTermMemoryManager
import com.agentx.core.memory.MemoryEntry
import com.agentx.data.repository.ModelRepository
import com.agentx.domain.models.GGUFModel
import kotlinx.coroutines.flow.first

data class GatewayServerInfo(
    val status: String = "online",
    val servingMode: String = "LOCAL_MODEL",
    val activeModel: String = "",
    val uptimeSeconds: Long = 0L,
    val latencyMs: Long = 0L,
    val port: Int = 8080
)

data class SettingsUiState(
    val tools: List<Tool> = emptyList(),
    val permissions: List<ToolPermission> = emptyList(),
    val executions: List<ExecutionLog> = emptyList(),
    val activeExecutionState: ExecutionState? = null,
    val darkTheme: Boolean = true,
    val hapticFeedback: Boolean = true,
    val maxCpuThreads: Int = 4,
    val enableMlock: Boolean = false,
    val cloudConfig: CloudModelConfig = CloudModelConfig(),
    val cloudTestResult: String? = null,
    val isTestingConnection: Boolean = false,
    val isFetchingModels: Boolean = false,
    val availableGatewayModels: List<String> = emptyList(),
    val serverDetails: GatewayServerInfo? = null,
    val discoveredHubs: List<DiscoveredGatewayHub> = emptyList(),
    val activeModel: GGUFModel? = null,
    val allModels: List<GGUFModel> = emptyList(),
    val modelStatus: ModelStatus = ModelStatus(ModelLifecycleState.NOT_LOADED),
    val multimodalQuality: String = "BALANCED",
    val multimodalInputEnabled: Boolean = true,
    val mcpServers: List<McpServerDescriptor> = emptyList(),
    val storedMemories: List<MemoryEntry> = emptyList(),
    val pairingCodeInput: String = "",
    val isPairing: Boolean = false,
    val pairingMessage: String? = null,
    val isPairingSuccess: Boolean = false,
    val pairedServerName: String? = null
)

class SettingsViewModel(
    private val toolRepository: ToolRepository,
    private val executionRepository: ExecutionRepository,
    private val executionQueue: ExecutionQueue? = null,
    private val hybridInferenceEngine: HybridInferenceEngine? = null,
    private val nsdDiscovery: GatewayNsdDiscovery? = null,
    private val preferencesManager: com.agentx.data.local.SettingsPreferencesManager? = null,
    private val modelRepository: ModelRepository? = null,
    private val inferenceEngine: InferenceEngine? = null,
    private val memoryManager: DefaultLongTermMemoryManager? = null,
    private val mcpRegistry: McpServerRegistry? = null
) : ViewModel() {

    private val _darkTheme = MutableStateFlow(preferencesManager?.loadDarkTheme() ?: true)
    private val _hapticFeedback = MutableStateFlow(preferencesManager?.loadHapticFeedback() ?: true)
    private val _threads = MutableStateFlow(preferencesManager?.loadThreads() ?: 4)
    private val _mlock = MutableStateFlow(preferencesManager?.loadMlock() ?: false)
    private val _multimodalQuality = MutableStateFlow(preferencesManager?.loadMultimodalQuality() ?: "BALANCED")
    private val _multimodalInputEnabled = MutableStateFlow(preferencesManager?.loadMultimodalInputEnabled() ?: true)
    private val _modelStatus = MutableStateFlow(inferenceEngine?.getModelStatus() ?: ModelStatus(ModelLifecycleState.NOT_LOADED))
    private val _storedMemories = MutableStateFlow<List<MemoryEntry>>(emptyList())
    private val _cloudConfig = MutableStateFlow(
        preferencesManager?.loadCloudConfig() ?: hybridInferenceEngine?.cloudEngine?.config ?: CloudModelConfig()
    )
    private val _cloudTestResult = MutableStateFlow<String?>(null)
    private val _isTestingConnection = MutableStateFlow(false)
    private val _isFetchingModels = MutableStateFlow(false)
    private val _availableGatewayModels = MutableStateFlow<List<String>>(emptyList())
    private val _serverDetails = MutableStateFlow<GatewayServerInfo?>(null)

    private val _pairingCodeInput = MutableStateFlow("")
    private val _isPairing = MutableStateFlow(false)
    private val _pairingMessage = MutableStateFlow<String?>(null)
    private val _isPairingSuccess = MutableStateFlow(false)
    private val _pairedServerName = MutableStateFlow(preferencesManager?.loadPairedServerName())

    private data class CloudGatewayState(
        val isTesting: Boolean,
        val isFetching: Boolean,
        val models: List<String>,
        val serverInfo: GatewayServerInfo?,
        val discoveredHubs: List<DiscoveredGatewayHub>
    )

    val discoveredHubsFlow: StateFlow<List<DiscoveredGatewayHub>> =
        nsdDiscovery?.discoveredHubs ?: MutableStateFlow(emptyList())

    private val gatewayFlow = combine(
        _isTestingConnection,
        _isFetchingModels,
        _availableGatewayModels,
        _serverDetails,
        discoveredHubsFlow
    ) { testing, fetching, models, info, hubs ->
        CloudGatewayState(testing, fetching, models, info, hubs)
    }

    private val preferencesFlow = combine(
        _darkTheme,
        _hapticFeedback,
        _threads,
        _mlock,
        _cloudConfig,
        _cloudTestResult
    ) { args ->
        SettingsPreferences(
            darkTheme = args[0] as Boolean,
            hapticFeedback = args[1] as Boolean,
            threads = args[2] as Int,
            mlock = args[3] as Boolean,
            cloudConfig = args[4] as CloudModelConfig,
            cloudTestResult = args[5] as? String
        )
    }

    private data class SettingsPreferences(
        val darkTheme: Boolean,
        val hapticFeedback: Boolean,
        val threads: Int,
        val mlock: Boolean,
        val cloudConfig: CloudModelConfig,
        val cloudTestResult: String?
    )

    private val executionFlow: StateFlow<ExecutionState> =
        executionQueue?.state ?: MutableStateFlow(ExecutionState())

    private data class CoreSettingsData(
        val tools: List<Tool>,
        val permissions: List<ToolPermission>,
        val executions: List<ExecutionLog>,
        val activeExecutionState: ExecutionState?
    )

    private val coreDataFlow = combine(
        toolRepository.getAllTools(),
        toolRepository.getAllPermissions(),
        executionRepository.getRecentExecutions(),
        executionFlow
    ) { tools, permissions, executions, execState ->
        CoreSettingsData(
            tools = tools,
            permissions = permissions,
            executions = executions,
            activeExecutionState = if (execState.status == ExecutionStatus.RUNNING) execState else null
        )
    }

    private data class RuntimeSubsystemState(
        val activeModel: GGUFModel?,
        val allModels: List<GGUFModel>,
        val modelStatus: ModelStatus,
        val multimodalQuality: String,
        val multimodalInputEnabled: Boolean,
        val mcpServers: List<McpServerDescriptor>,
        val memories: List<MemoryEntry>
    )

    private val runtimeSubsystemFlow = combine(
        modelRepository?.getActiveModel() ?: kotlinx.coroutines.flow.flowOf(null),
        modelRepository?.getAllModels() ?: kotlinx.coroutines.flow.flowOf(emptyList()),
        _modelStatus,
        _multimodalQuality,
        _multimodalInputEnabled
    ) { activeModel, allModels, modelStatus, quality, inputEnabled ->
        val mcpList = mcpRegistry?.serverDescriptors?.value ?: emptyList()
        val memList = _storedMemories.value
        RuntimeSubsystemState(
            activeModel = activeModel,
            allModels = allModels,
            modelStatus = modelStatus,
            multimodalQuality = quality,
            multimodalInputEnabled = inputEnabled,
            mcpServers = mcpList,
            memories = memList
        )
    }

    val uiState: StateFlow<SettingsUiState> = combine(
        coreDataFlow,
        preferencesFlow,
        gatewayFlow,
        runtimeSubsystemFlow
    ) { core, prefs, gw, runtime ->
        SettingsUiState(
            tools = core.tools,
            permissions = core.permissions,
            executions = core.executions,
            activeExecutionState = core.activeExecutionState,
            darkTheme = prefs.darkTheme,
            hapticFeedback = prefs.hapticFeedback,
            maxCpuThreads = prefs.threads,
            enableMlock = prefs.mlock,
            cloudConfig = prefs.cloudConfig,
            cloudTestResult = prefs.cloudTestResult,
            isTestingConnection = gw.isTesting,
            isFetchingModels = gw.isFetching,
            availableGatewayModels = gw.models,
            serverDetails = gw.serverInfo,
            discoveredHubs = gw.discoveredHubs,
            activeModel = runtime.activeModel,
            allModels = runtime.allModels,
            modelStatus = runtime.modelStatus,
            multimodalQuality = runtime.multimodalQuality,
            multimodalInputEnabled = runtime.multimodalInputEnabled,
            mcpServers = runtime.mcpServers,
            storedMemories = runtime.memories,
            pairingCodeInput = _pairingCodeInput.value,
            isPairing = _isPairing.value,
            pairingMessage = _pairingMessage.value,
            isPairingSuccess = _isPairingSuccess.value,
            pairedServerName = _pairedServerName.value
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = SettingsUiState()
    )

    init {
        nsdDiscovery?.startDiscovery()
        refreshMemories()
    }

    fun refreshMemories() {
        viewModelScope.launch(Dispatchers.IO) {
            val list = memoryManager?.getAll() ?: emptyList()
            _storedMemories.value = list
        }
    }

    fun clearAllMemories() {
        viewModelScope.launch(Dispatchers.IO) {
            memoryManager?.clearAll()
            _storedMemories.value = emptyList()
        }
    }

    fun deleteMemory(id: String) {
        viewModelScope.launch(Dispatchers.IO) {
            memoryManager?.delete(id)
            refreshMemories()
        }
    }

    fun setMultimodalQuality(quality: String) {
        _multimodalQuality.value = quality
        preferencesManager?.saveMultimodalQuality(quality)
    }

    fun setMultimodalInputEnabled(enabled: Boolean) {
        _multimodalInputEnabled.value = enabled
        preferencesManager?.saveMultimodalInputEnabled(enabled)
    }

    fun loadActiveModelIntoRam() {
        viewModelScope.launch(Dispatchers.IO) {
            val model = modelRepository?.getActiveModel()?.first() ?: return@launch
            try {
                inferenceEngine?.loadModel(
                    ModelConfig(
                        modelId = model.id,
                        modelPath = model.filePath,
                        contextLength = model.contextLength,
                        threads = _threads.value
                    )
                )
            } catch (_: Exception) {}
            _modelStatus.value = inferenceEngine?.getModelStatus() ?: ModelStatus(ModelLifecycleState.LOADED)
        }
    }

    fun unloadActiveModelFromRam() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                inferenceEngine?.unloadModel()
            } catch (_: Exception) {}
            _modelStatus.value = inferenceEngine?.getModelStatus() ?: ModelStatus(ModelLifecycleState.NOT_LOADED)
        }
    }

    fun switchActiveModel(modelId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            modelRepository?.setActiveModel(modelId)
            loadActiveModelIntoRam()
        }
    }

    fun registerRemoteMcpServer(serverName: String, endpointUrl: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val client = HttpMcpClient(serverName = serverName, endpointUrl = endpointUrl)
            mcpRegistry?.registerServer(client, endpointUrl = endpointUrl, isLocal = false)
        }
    }

    fun startLanDiscovery() {
        nsdDiscovery?.startDiscovery()
    }

    fun stopLanDiscovery() {
        nsdDiscovery?.stopDiscovery()
    }

    override fun onCleared() {
        super.onCleared()
        nsdDiscovery?.stopDiscovery()
    }

    fun setInferenceMode(mode: InferenceMode) {
        val updated = _cloudConfig.value.copy(inferenceMode = mode)
        _cloudConfig.value = updated
        hybridInferenceEngine?.inferenceMode = mode
        hybridInferenceEngine?.cloudEngine?.config = updated
        preferencesManager?.saveCloudConfig(updated)
    }

    fun updateCloudConfig(
        provider: CloudProvider? = null,
        apiKey: String? = null,
        geminiApiKey: String? = null,
        gatewayApiKey: String? = null,
        modelName: String? = null,
        customBaseUrl: String? = null
    ) {
        val current = _cloudConfig.value
        val resolvedProvider = provider ?: current.provider
        val updated = current.copy(
            provider = resolvedProvider,
            geminiApiKey = geminiApiKey
                ?: if (apiKey != null && resolvedProvider == CloudProvider.GEMINI) apiKey else current.geminiApiKey,
            gatewayApiKey = gatewayApiKey
                ?: if (apiKey != null && resolvedProvider == CloudProvider.CUSTOM_GATEWAY) apiKey else current.gatewayApiKey,
            modelName = modelName ?: current.modelName,
            customBaseUrl = customBaseUrl ?: current.customBaseUrl
        )
        _cloudConfig.value = updated
        hybridInferenceEngine?.cloudEngine?.config = updated
        preferencesManager?.saveCloudConfig(updated)
    }

    fun testCloudConnection() {
        viewModelScope.launch {
            _isTestingConnection.value = true
            _cloudTestResult.value = "Testing gateway connectivity..."
            try {
                val config = _cloudConfig.value
                if (config.provider == CloudProvider.GEMINI) {
                    val geminiApiKey = config.apiKeyFor(CloudProvider.GEMINI)
                    if (geminiApiKey.isBlank()) {
                        _cloudTestResult.value = "Error: Gemini API Key is empty. Configure key above."
                        return@launch
                    }
                    val start = System.currentTimeMillis()
                    withContext(Dispatchers.IO) {
                        val pingUrl = URL("https://generativelanguage.googleapis.com/v1beta/models?key=$geminiApiKey")
                        val conn = (pingUrl.openConnection() as HttpURLConnection).apply {
                            requestMethod = "GET"
                            connectTimeout = 8_000
                            readTimeout = 8_000
                        }
                        val code = conn.responseCode
                        val latency = System.currentTimeMillis() - start
                        if (code in 200..299) {
                            _cloudTestResult.value = "Verified! Connected to Gemini (${config.modelName}) • Ping: ${latency}ms"
                        } else {
                            val err = conn.errorStream?.bufferedReader()?.use { it.readText() } ?: "HTTP $code"
                            _cloudTestResult.value = "Gemini Auth Error ($code): $err"
                        }
                    }
                    return@launch
                }

                // CUSTOM_GATEWAY: Real network ping to AgentX Serve
                val endpoints = GatewayUrlNormalizer.normalize(config.customBaseUrl)
                val gatewayApiKey = config.apiKeyFor(CloudProvider.CUSTOM_GATEWAY)
                val (serverInfo, models) = withContext(Dispatchers.IO) {
                    val start = System.currentTimeMillis()
                    val healthUrl = URL(endpoints.healthUrl)
                    val conn = (healthUrl.openConnection() as HttpURLConnection).apply {
                        requestMethod = "GET"
                        connectTimeout = 6_000
                        readTimeout = 6_000
                        if (gatewayApiKey.isNotBlank()) {
                            setRequestProperty("Authorization", "Bearer ${config.apiKey.trim()}")
                            setRequestProperty("X-API-Key", gatewayApiKey)
                        }
                    }

                    val code = conn.responseCode
                    val latency = System.currentTimeMillis() - start

                    if (code in 200..299) {
                        val body = conn.inputStream.bufferedReader().use { it.readText() }
                        val parsed = try {
                            Json.parseToJsonElement(body).jsonObject
                        } catch (_: Exception) { null }

                        val servingMode = parsed?.get("servingMode")?.jsonPrimitive?.content ?: "LOCAL_MODEL"
                        val activeLocal = parsed?.get("activeLocalModel")?.jsonPrimitive?.content
                            ?: parsed?.get("activeCloudModel")?.jsonPrimitive?.content
                            ?: "Active Model"
                        val uptime = parsed?.get("uptimeSeconds")?.jsonPrimitive?.content?.toLongOrNull() ?: 0L

                        val fetchedModels = queryModelsFromGateway(endpoints, gatewayApiKey)

                        val info = GatewayServerInfo(
                            status = "online",
                            servingMode = servingMode,
                            activeModel = activeLocal,
                            uptimeSeconds = uptime,
                            latencyMs = latency
                        )
                        Pair(info, fetchedModels)
                    } else {
                        val err = conn.errorStream?.bufferedReader()?.use { it.readText() } ?: "HTTP $code"
                        val msg = when (code) {
                            401 -> "401 Unauthorized: Invalid API Key. Check the API Key generated in Serve app."
                            403 -> "403 Forbidden: Client IP has been blocked by Serve administrator."
                            404 -> "404 Endpoint Not Found: Server reachable but endpoint missing. Check Gateway URL."
                            else -> "Gateway Error ($code): $err"
                        }
                        throw IllegalStateException(msg)
                    }
                }

                _serverDetails.value = serverInfo
                if (models.isNotEmpty()) {
                    _availableGatewayModels.value = models
                    if (config.modelName.isBlank() || config.modelName == "gemini-2.5-flash") {
                        val preferred = if (models.contains(serverInfo.activeModel)) serverInfo.activeModel else models.first()
                        updateCloudConfig(modelName = preferred)
                    }
                }
                _cloudTestResult.value = "Verified! Connected to AgentX Serve (${serverInfo.latencyMs}ms) • Active: ${serverInfo.activeModel}"
            } catch (e: Exception) {
                _serverDetails.value = null
                val friendlyMessage = when (e) {
                    is java.net.ConnectException -> "Connection Refused: Make sure AgentX Serve is turned ON and running on port 8080."
                    is java.net.SocketTimeoutException -> "Timed Out (6s): Check Wi-Fi network. Ensure both devices are on the same Wi-Fi subnet."
                    is java.net.MalformedURLException -> "Malformed URL: Enter e.g. http://192.168.1.5:8080"
                    else -> e.message ?: "Connection failed"
                }
                _cloudTestResult.value = "Failed: $friendlyMessage"
            } finally {
                _isTestingConnection.value = false
            }
        }
    }

    fun fetchGatewayModels() {
        viewModelScope.launch {
            _isFetchingModels.value = true
            try {
                val config = _cloudConfig.value
                val endpoints = GatewayUrlNormalizer.normalize(config.customBaseUrl)
                val gatewayApiKey = config.apiKeyFor(CloudProvider.CUSTOM_GATEWAY)
                val models = withContext(Dispatchers.IO) {
                    queryModelsFromGateway(endpoints, gatewayApiKey)
                }
                if (models.isNotEmpty()) {
                    _availableGatewayModels.value = models
                    _cloudTestResult.value = "Fetched ${models.size} models from AgentX Serve Gateway"
                    if (config.modelName.isBlank() || config.modelName == "gemini-2.5-flash" || !models.contains(config.modelName)) {
                        updateCloudConfig(modelName = models.first())
                    }
                } else {
                    _cloudTestResult.value = "Gateway reachable, but no models reported"
                }
            } catch (e: Exception) {
                val msg = when (e) {
                    is java.net.ConnectException -> "Connection Refused: Ensure AgentX Serve is running."
                    is java.net.SocketTimeoutException -> "Timed Out: Verify Wi-Fi network IP."
                    else -> e.message ?: "Fetch failed"
                }
                _cloudTestResult.value = "Fetch error: $msg"
            } finally {
                _isFetchingModels.value = false
            }
        }
    }

    private fun queryModelsFromGateway(endpoints: GatewayEndpoints, apiKey: String): List<String> {
        val modelsUrl = URL(endpoints.modelsUrl)
        val conn = (modelsUrl.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 6_000
            readTimeout = 6_000
            if (apiKey.isNotBlank()) {
                setRequestProperty("Authorization", "Bearer ${apiKey.trim()}")
                setRequestProperty("X-API-Key", apiKey.trim())
            }
        }
        val code = conn.responseCode
        if (code !in 200..299) return emptyList()
        val body = conn.inputStream.bufferedReader().use { it.readText() }
        val parsed = Json.parseToJsonElement(body).jsonObject
        val data = parsed["data"]?.jsonArray ?: return emptyList()
        return data.mapNotNull { it.jsonObject["id"]?.jsonPrimitive?.content }
    }

    fun clearCloudTestResult() {
        _cloudTestResult.value = null
    }

    fun toggleToolPermission(toolId: String, granted: Boolean) {
        viewModelScope.launch {
            toolRepository.updatePermission(
                ToolPermission(
                    toolId = toolId,
                    granted = granted,
                    grantedAt = System.currentTimeMillis()
                )
            )
        }
    }

    fun setDarkTheme(enabled: Boolean) {
        _darkTheme.value = enabled
        preferencesManager?.saveDarkTheme(enabled)
    }

    fun setHapticFeedback(enabled: Boolean) {
        _hapticFeedback.value = enabled
        preferencesManager?.saveHapticFeedback(enabled)
    }

    fun setCpuThreads(threads: Int) {
        _threads.value = threads
        preferencesManager?.saveThreads(threads)
    }

    fun setMlock(enabled: Boolean) {
        _mlock.value = enabled
        preferencesManager?.saveMlock(enabled)
    }

    fun onPairingCodeInputChanged(code: String) {
        _pairingCodeInput.value = code
    }

    fun clearPairingMessage() {
        _pairingMessage.value = null
    }

    fun pairWith6DigitCode(code: String) {
        val sanitized = code.trim().replace("-", "").replace(" ", "")
        if (sanitized.length != 6) {
            _pairingMessage.value = "Please enter a valid 6-digit pairing code."
            _isPairingSuccess.value = false
            return
        }

        viewModelScope.launch {
            _isPairing.value = true
            _pairingMessage.value = "Resolving pairing code $sanitized from Cloud Rendezvous..."
            val rendezvous = com.agentx.core.protocol.pairing.RendezvousService()
            val payload = rendezvous.resolve(sanitized)
            if (payload != null) {
                applyPairingPayload(payload)
            } else {
                _pairingMessage.value = "Could not find server with code $sanitized. Ensure Serve app is running or scan the QR code."
                _isPairingSuccess.value = false
                _isPairing.value = false
            }
        }
    }

    fun pairWithQrString(qrString: String) {
        val trimmed = qrString.trim()
        if (trimmed.isBlank()) return

        viewModelScope.launch {
            _isPairing.value = true
            _pairingMessage.value = "Parsing QR pairing coordinates..."
            val payload = com.agentx.core.protocol.pairing.PairingPayload.fromQrString(trimmed)
            if (payload != null) {
                applyPairingPayload(payload)
            } else {
                _pairingMessage.value = "Unrecognized QR format. Please scan an AgentX Serve pairing code."
                _isPairingSuccess.value = false
                _isPairing.value = false
            }
        }
    }

    private suspend fun applyPairingPayload(payload: com.agentx.core.protocol.pairing.PairingPayload) {
        val targetEndpoint = payload.preferredEndpoint()
        val currentCfg = _cloudConfig.value
        val updatedCfg = currentCfg.copy(
            provider = CloudProvider.CUSTOM_GATEWAY,
            customBaseUrl = targetEndpoint,
            gatewayApiKey = payload.apiKey,
            modelName = payload.activeModel.ifBlank { currentCfg.modelName.ifBlank { "medimind-v2-q5_k_m" } },
            inferenceMode = InferenceMode.CLOUD_ONLY,
            isEnabled = true
        )

        _cloudConfig.value = updatedCfg
        preferencesManager?.saveCloudConfig(updatedCfg)
        preferencesManager?.saveSavedPairingCode(payload.code)
        preferencesManager?.savePairedServerName(payload.serverName)
        _pairedServerName.value = payload.serverName

        hybridInferenceEngine?.cloudEngine?.config = updatedCfg
        hybridInferenceEngine?.inferenceMode = InferenceMode.CLOUD_ONLY

        // Attempt direct P2P DataChannel connection if candidate ticket is provided
        payload.p2pTicket?.let { ticket ->
            if (ticket.startsWith("udp:")) {
                val parts = ticket.removePrefix("udp:").split(":")
                if (parts.size == 2) {
                    try {
                        val p2p = com.agentx.core.protocol.p2p.P2PDataChannel()
                        p2p.connectToPeer(parts[0], parts[1].toIntOrNull() ?: 49152)
                        hybridInferenceEngine?.cloudEngine?.p2pChannel = p2p
                    } catch (_: Exception) {}
                }
            }
        }

        _isPairingSuccess.value = true
        _pairingMessage.value = "Paired to ${payload.serverName}! Verifying gateway..."
        _isPairing.value = false
        testCloudConnection()
    }
}
