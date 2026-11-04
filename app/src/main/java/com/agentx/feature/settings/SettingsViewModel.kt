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
    val maxCpuThreads: Int = 4,
    val enableMlock: Boolean = false,
    val cloudConfig: CloudModelConfig = CloudModelConfig(),
    val cloudTestResult: String? = null,
    val isTestingConnection: Boolean = false,
    val isFetchingModels: Boolean = false,
    val availableGatewayModels: List<String> = emptyList(),
    val serverDetails: GatewayServerInfo? = null,
    val discoveredHubs: List<DiscoveredGatewayHub> = emptyList()
)

class SettingsViewModel(
    private val toolRepository: ToolRepository,
    private val executionRepository: ExecutionRepository,
    private val executionQueue: ExecutionQueue? = null,
    private val hybridInferenceEngine: HybridInferenceEngine? = null,
    private val nsdDiscovery: GatewayNsdDiscovery? = null,
    private val preferencesManager: com.agentx.data.local.SettingsPreferencesManager? = null
) : ViewModel() {

    private val _darkTheme = MutableStateFlow(preferencesManager?.loadDarkTheme() ?: true)
    private val _threads = MutableStateFlow(preferencesManager?.loadThreads() ?: 4)
    private val _mlock = MutableStateFlow(preferencesManager?.loadMlock() ?: false)
    private val _cloudConfig = MutableStateFlow(
        preferencesManager?.loadCloudConfig() ?: hybridInferenceEngine?.cloudEngine?.config ?: CloudModelConfig()
    )
    private val _cloudTestResult = MutableStateFlow<String?>(null)
    private val _isTestingConnection = MutableStateFlow(false)
    private val _isFetchingModels = MutableStateFlow(false)
    private val _availableGatewayModels = MutableStateFlow<List<String>>(emptyList())
    private val _serverDetails = MutableStateFlow<GatewayServerInfo?>(null)

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
        _threads,
        _mlock,
        _cloudConfig,
        _cloudTestResult
    ) { darkTheme, threads, mlock, cloudConfig, cloudTest ->
        SettingsPreferences(darkTheme, threads, mlock, cloudConfig, cloudTest)
    }

    private data class SettingsPreferences(
        val darkTheme: Boolean,
        val threads: Int,
        val mlock: Boolean,
        val cloudConfig: CloudModelConfig,
        val cloudTestResult: String?
    )

    private val executionFlow: StateFlow<ExecutionState> =
        executionQueue?.state ?: MutableStateFlow(ExecutionState())

    val uiState: StateFlow<SettingsUiState> = combine(
        toolRepository.getAllTools(),
        toolRepository.getAllPermissions(),
        executionRepository.getRecentExecutions(),
        combine(preferencesFlow, gatewayFlow) { prefs, gw -> Pair(prefs, gw) },
        executionFlow
    ) { tools, permissions, executions, (prefs, gw), execState ->
        SettingsUiState(
            tools = tools,
            permissions = permissions,
            executions = executions,
            activeExecutionState = if (execState.status == ExecutionStatus.RUNNING) execState else null,
            darkTheme = prefs.darkTheme,
            maxCpuThreads = prefs.threads,
            enableMlock = prefs.mlock,
            cloudConfig = prefs.cloudConfig,
            cloudTestResult = prefs.cloudTestResult,
            isTestingConnection = gw.isTesting,
            isFetchingModels = gw.isFetching,
            availableGatewayModels = gw.models,
            serverDetails = gw.serverInfo,
            discoveredHubs = gw.discoveredHubs
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = SettingsUiState()
    )


    init {
        nsdDiscovery?.startDiscovery()
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
        modelName: String? = null,
        customBaseUrl: String? = null
    ) {
        val current = _cloudConfig.value
        val updated = current.copy(
            provider = provider ?: current.provider,
            apiKey = apiKey ?: current.apiKey,
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
                    if (config.apiKey.isBlank()) {
                        _cloudTestResult.value = "Error: Gemini API Key is empty. Configure key above."
                        return@launch
                    }
                    val start = System.currentTimeMillis()
                    withContext(Dispatchers.IO) {
                        val pingUrl = URL("https://generativelanguage.googleapis.com/v1beta/models?key=${config.apiKey.trim()}")
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
                val (serverInfo, models) = withContext(Dispatchers.IO) {
                    val start = System.currentTimeMillis()
                    val healthUrl = URL(endpoints.healthUrl)
                    val conn = (healthUrl.openConnection() as HttpURLConnection).apply {
                        requestMethod = "GET"
                        connectTimeout = 6_000
                        readTimeout = 6_000
                        if (config.apiKey.isNotBlank()) {
                            setRequestProperty("Authorization", "Bearer ${config.apiKey.trim()}")
                            setRequestProperty("X-API-Key", config.apiKey.trim())
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

                        val fetchedModels = queryModelsFromGateway(endpoints, config.apiKey)

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
                val models = withContext(Dispatchers.IO) {
                    queryModelsFromGateway(endpoints, config.apiKey)
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

    fun setCpuThreads(threads: Int) {
        _threads.value = threads
        preferencesManager?.saveThreads(threads)
    }

    fun setMlock(enabled: Boolean) {
        _mlock.value = enabled
        preferencesManager?.saveMlock(enabled)
    }
}
