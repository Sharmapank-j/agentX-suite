package com.agentx.admin.ui

import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.agentx.admin.AgentXAdminApplication
import com.agentx.admin.models.ApiKeyRecord
import com.agentx.admin.models.ClientSession
import com.agentx.admin.models.ServerTelemetry
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

    private val _selectedPort = MutableStateFlow(8080)
    private val _selectedTab = MutableStateFlow(0)
    private val _geminiApiKey = MutableStateFlow(cloudGateway.geminiApiKey)
    private val _activeModel = MutableStateFlow(cloudGateway.activeModelName)
    private val _cloudStatus = MutableStateFlow<String?>(null)
    private val _isTestingCloud = MutableStateFlow(false)
    private val _notification = MutableStateFlow<String?>(null)

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
        combine(clientManager.trafficLogs, serverSettingsFlow, cloudSettingsFlow) { logs, (port, tab, key), cloudPrefs ->
            SubAdminState(logs, port, tab, key, cloudPrefs)
        }
    ) { telemetry, clients, apiKeys, subState ->
        AdminUiState(
            telemetry = telemetry,
            clients = clients,
            apiKeys = apiKeys,
            trafficLogs = subState.logs,
            selectedPort = subState.port,
            selectedTab = subState.tab,
            geminiApiKey = subState.key,
            activeModel = subState.cloudPrefs.model,
            cloudConnectionStatus = subState.cloudPrefs.status,
            isTestingCloud = subState.cloudPrefs.testing,
            notificationMessage = subState.cloudPrefs.notification
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = AdminUiState()
    )

    private data class SubAdminState(
        val logs: List<TrafficLog>,
        val port: Int,
        val tab: Int,
        val key: String,
        val cloudPrefs: CloudPreferences
    )

    fun selectTab(tabIndex: Int) {
        _selectedTab.value = tabIndex
    }

    fun setPort(port: Int) {
        if (port in 1024..65535) {
            _selectedPort.value = port
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
