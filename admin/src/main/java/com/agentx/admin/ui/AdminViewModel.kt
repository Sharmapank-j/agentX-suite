package com.agentx.admin.ui

import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.agentx.admin.AgentXAdminApplication
import com.agentx.admin.models.ApiKeyRecord
import com.agentx.admin.models.ClientSession
import com.agentx.admin.models.LocalModelStatus
import com.agentx.admin.models.LocalServerModel
import com.agentx.admin.models.ServerTelemetry
import com.agentx.admin.models.ServingMode
import com.agentx.admin.models.TrafficLog
import com.agentx.admin.service.AgentXServerService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class AdminUiState(
    val telemetry: ServerTelemetry = ServerTelemetry(),
    val clients: List<ClientSession> = emptyList(),
    val apiKeys: List<ApiKeyRecord> = emptyList(),
    val trafficLogs: List<TrafficLog> = emptyList(),
    val selectedPort: Int = 8080,
    val selectedTab: Int = 0,
    val servingMode: ServingMode = ServingMode.LOCAL_MODEL,
    val curatedLocalModels: List<LocalServerModel> = emptyList(),
    val activeLocalModel: LocalServerModel? = null,
    val localModelStatus: LocalModelStatus = LocalModelStatus.LOADED,
    val testPrompt: String = "Explain the differential diagnosis of acute chest pain.",
    val testResponse: String? = null,
    val isGeneratingTest: Boolean = false,
    val testLatencyMs: Long = 0L,
    val geminiApiKey: String = "",
    val activeModel: String = "gemini-2.5-flash",
    val cloudConnectionStatus: String? = null,
    val isTestingCloud: Boolean = false,
    val notificationMessage: String? = null
)

class AdminViewModel(
    private val app: AgentXAdminApplication
) : ViewModel() {

    private val server = app.server
    private val clientManager = app.clientManager
    private val cloudGateway = app.cloudGateway
    private val localEngine = app.localEngine

    private val _selectedPort = MutableStateFlow(8080)
    private val _selectedTab = MutableStateFlow(0)
    private val _servingMode = MutableStateFlow(server.getServingMode())
    private val _curatedLocalModels = MutableStateFlow(localEngine.curatedCatalog)
    private val _activeLocalModel = MutableStateFlow(localEngine.activeModel)
    private val _localModelStatus = MutableStateFlow(localEngine.status)
    private val _testPrompt = MutableStateFlow("Explain the differential diagnosis of acute chest pain.")
    private val _testResponse = MutableStateFlow<String?>(null)
    private val _isGeneratingTest = MutableStateFlow(false)
    private val _testLatencyMs = MutableStateFlow(0L)

    private val _geminiApiKey = MutableStateFlow(cloudGateway.geminiApiKey)
    private val _activeModel = MutableStateFlow(cloudGateway.activeModelName)
    private val _cloudStatus = MutableStateFlow<String?>(null)
    private val _isTestingCloud = MutableStateFlow(false)
    private val _notification = MutableStateFlow<String?>(null)

    private data class LocalEnginePrefs(
        val servingMode: ServingMode,
        val curatedCatalog: List<LocalServerModel>,
        val activeModel: LocalServerModel,
        val status: LocalModelStatus,
        val testPrompt: String,
        val testResponse: String?,
        val isGeneratingTest: Boolean,
        val testLatencyMs: Long
    )

    private val localEnginePrefsFlow = combine(
        combine(_servingMode, _curatedLocalModels, _activeLocalModel, _localModelStatus) { mode, catalog, active, status ->
            Quad(mode, catalog, active, status)
        },
        combine(_testPrompt, _testResponse, _isGeneratingTest, _testLatencyMs) { prompt, response, generating, latency ->
            Quad(prompt, response, generating, latency)
        }
    ) { q1, q2 ->
        LocalEnginePrefs(
            servingMode = q1.first,
            curatedCatalog = q1.second,
            activeModel = q1.third,
            status = q1.fourth,
            testPrompt = q2.first,
            testResponse = q2.second,
            isGeneratingTest = q2.third,
            testLatencyMs = q2.fourth
        )
    }

    private data class Quad<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)

    private val serverSettingsFlow = combine(_selectedPort, _selectedTab, _geminiApiKey) { port, tab, key ->
        Triple(port, tab, key)
    }

    private val cloudSettingsFlow = combine(_activeModel, _cloudStatus, _isTestingCloud, _notification) { model, status, testing, notif ->
        CloudPreferences(model, status, testing, notif)
    }

    private data class CloudPreferences(
        val model: String,
        val status: String?,
        val testing: Boolean,
        val notification: String?
    )

    val uiState: StateFlow<AdminUiState> = combine(
        server.telemetry,
        clientManager.clients,
        clientManager.apiKeys,
        clientManager.trafficLogs,
        combine(serverSettingsFlow, cloudSettingsFlow, localEnginePrefsFlow) { (port, tab, key), cloudPrefs, localPrefs ->
            SubAdminState(port, tab, key, cloudPrefs, localPrefs)
        }
    ) { telemetry, clients, apiKeys, logs, subState ->
        AdminUiState(
            telemetry = telemetry,
            clients = clients,
            apiKeys = apiKeys,
            trafficLogs = logs,
            selectedPort = subState.port,
            selectedTab = subState.tab,
            servingMode = subState.localPrefs.servingMode,
            curatedLocalModels = subState.localPrefs.curatedCatalog,
            activeLocalModel = subState.localPrefs.activeModel,
            localModelStatus = subState.localPrefs.status,
            testPrompt = subState.localPrefs.testPrompt,
            testResponse = subState.localPrefs.testResponse,
            isGeneratingTest = subState.localPrefs.isGeneratingTest,
            testLatencyMs = subState.localPrefs.testLatencyMs,
            geminiApiKey = subState.key,
            activeModel = subState.cloudPrefs.model,
            cloudConnectionStatus = subState.cloudPrefs.status,
            isTestingCloud = subState.cloudPrefs.testing,
            notificationMessage = subState.cloudPrefs.notification
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = AdminUiState(
            curatedLocalModels = localEngine.curatedCatalog,
            activeLocalModel = localEngine.activeModel,
            localModelStatus = localEngine.status
        )
    )

    private data class SubAdminState(
        val port: Int,
        val tab: Int,
        val key: String,
        val cloudPrefs: CloudPreferences,
        val localPrefs: LocalEnginePrefs
    )

    fun selectTab(tabIndex: Int) {
        _selectedTab.value = tabIndex
    }

    fun setPort(port: Int) {
        if (port in 1024..65535) {
            _selectedPort.value = port
        }
    }

    fun setServingMode(mode: ServingMode) {
        _servingMode.value = mode
        server.setServingMode(mode)
        _notification.value = "Serving mode set to: ${mode.label}"
    }

    fun loadLocalModel(modelId: String) {
        viewModelScope.launch {
            _localModelStatus.value = LocalModelStatus.LOADING
            _notification.value = "Loading model $modelId into AirLLM runtime..."
            val ok = server.switchLocalModel(modelId)
            if (ok) {
                _activeLocalModel.value = localEngine.activeModel
                _localModelStatus.value = localEngine.status
                _curatedLocalModels.value = localEngine.curatedCatalog.map {
                    if (it.id == modelId) it.copy(isLoaded = true, isServing = true)
                    else it.copy(isLoaded = false, isServing = false)
                }
                _notification.value = "Active model switched to ${localEngine.activeModel.name}"
            } else {
                _localModelStatus.value = localEngine.status
                _notification.value = "Failed to load model $modelId"
            }
        }
    }

    fun unloadLocalModel() {
        viewModelScope.launch {
            server.unloadLocalModel()
            _activeLocalModel.value = localEngine.activeModel
            _localModelStatus.value = localEngine.status
            _curatedLocalModels.value = localEngine.curatedCatalog.map {
                it.copy(isLoaded = false, isServing = false)
            }
            _notification.value = "Unloaded local model from memory."
        }
    }

    fun setTestPrompt(prompt: String) {
        _testPrompt.value = prompt
    }

    fun clearTestResponse() {
        _testResponse.value = null
        _testLatencyMs.value = 0L
    }

    fun runLocalModelTest() {
        viewModelScope.launch {
            _isGeneratingTest.value = true
            _testResponse.value = ""
            val start = System.currentTimeMillis()
            val prompt = _testPrompt.value.ifBlank { "Explain the differential diagnosis of acute chest pain." }

            try {
                localEngine.generateCompletionStream(prompt = prompt).collect { token ->
                    _testResponse.value = (_testResponse.value ?: "") + token
                }
                _testLatencyMs.value = System.currentTimeMillis() - start
            } catch (e: Exception) {
                _testResponse.value = "Error generating inference: ${e.message}"
            } finally {
                _isGeneratingTest.value = false
            }
        }
    }

    fun toggleServer(enable: Boolean, context: Context) {
        val intent = Intent(context, AgentXServerService::class.java).apply {
            if (enable) {
                action = AgentXServerService.ACTION_START
                putExtra(AgentXServerService.EXTRA_PORT, _selectedPort.value)
            } else {
                action = AgentXServerService.ACTION_STOP
            }
        }
        if (enable) {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
            _notification.value = "Starting AgentX Serve on port ${_selectedPort.value}..."
        } else {
            context.startService(intent)
            _notification.value = "Stopping server..."
        }
    }

    fun setGeminiApiKey(key: String) {
        _geminiApiKey.value = key
        cloudGateway.geminiApiKey = key
    }

    fun setActiveModel(model: String) {
        _activeModel.value = model
        cloudGateway.activeModelName = model
    }

    fun testCloudConnection() {
        viewModelScope.launch {
            _isTestingCloud.value = true
            _cloudStatus.value = "Connecting to Google Gemini API..."
            val ok = cloudGateway.testConnection()
            if (ok) {
                _cloudStatus.value = "Online: Verified connection to ${cloudGateway.activeModelName}"
            } else {
                _cloudStatus.value = "Disconnected: Verify your API key or network connection."
            }
            _isTestingCloud.value = false
        }
    }

    fun createApiKey(name: String) {
        val record = clientManager.createApiKey(name)
        _notification.value = "Generated key for: ${record.clientName}"
    }

    fun revokeApiKey(key: String) {
        clientManager.revokeApiKey(key)
        _notification.value = "Key revoked"
    }

    fun toggleBlockClient(ipAddress: String) {
        val isBlocked = clientManager.isClientBlocked(ipAddress)
        clientManager.setClientBlocked(ipAddress, !isBlocked)
        _notification.value = if (!isBlocked) "Blocked $ipAddress" else "Unblocked $ipAddress"
    }

    fun clearNotification() {
        _notification.value = null
    }
}
