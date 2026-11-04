package com.agentx.admin.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class LiveTestConsoleMetrics(
    val prompt: String = "Explain how on-device AI models stream layers efficiently on mobile hardware.",
    val response: String? = null,
    val isGenerating: Boolean = false,
    val latencyMs: Long = 0L,
    val ttftMs: Long = 0L,
    val tokenCount: Int = 0,
    val tokensPerSecond: Double = 0.0,
    val modelTested: String = "",
    val engineType: String = "",
    val memoryUsageMb: Long = 0L,
    val status: String? = null
)

data class AdminUiState(
    val telemetry: ServerTelemetry = ServerTelemetry(),
    val clients: List<ClientSession> = emptyList(),
    val apiKeys: List<ApiKeyRecord> = emptyList(),
    val trafficLogs: List<TrafficLog> = emptyList(),
    val selectedPort: Int = 8080,
    val selectedTab: Int = 0,
    val servingMode: ServingMode = ServingMode.LOCAL_MODEL,
    val curatedLocalModels: List<LocalServerModel> = emptyList(),
    val storageModels: List<LocalServerModel> = emptyList(),
    val isScanningStorage: Boolean = false,
    val activeLocalModel: LocalServerModel? = null,
    val localModelStatus: LocalModelStatus = LocalModelStatus.LOADED,
    val testPrompt: String = "Explain how on-device AI models stream layers efficiently on mobile hardware.",
    val testResponse: String? = null,
    val isGeneratingTest: Boolean = false,
    val testLatencyMs: Long = 0L,
    val testTtftMs: Long = 0L,
    val testTokenCount: Int = 0,
    val testTokensPerSecond: Double = 0.0,
    val testModelTested: String = "",
    val testEngineType: String = "",
    val testMemoryUsageMb: Long = 0L,
    val testStatus: String? = null,
    val geminiApiKey: String = "",
    val activeModel: String = "gemini-2.5-flash",
    val cloudConnectionStatus: String? = null,
    val isTestingCloud: Boolean = false,
    val notificationMessage: String? = null,
    val thermalSnapshot: com.agentx.admin.server.ThermalSnapshot = com.agentx.admin.server.ThermalSnapshot(),
    val isTlsEnabled: Boolean = false,
    val tlsFingerprint: String = ""
)

class AdminViewModel(
    private val app: AgentXAdminApplication
) : ViewModel() {

    private val server = app.server
    private val clientManager = app.clientManager
    private val cloudGateway = app.cloudGateway
    private val localEngine = app.localEngine
    val thermalManager = app.thermalManager
    val tlsManager = app.tlsManager

    private val _selectedPort = MutableStateFlow(8080)
    private val _selectedTab = MutableStateFlow(0)
    private val _servingMode = MutableStateFlow(server.getServingMode())
    private val _curatedLocalModels = MutableStateFlow(localEngine.curatedCatalog)
    private val _storageModels = MutableStateFlow(localEngine.storageModels)
    private val _isScanningStorage = MutableStateFlow(false)
    private val _activeLocalModel = MutableStateFlow(localEngine.activeModel)
    private val _localModelStatus = MutableStateFlow(localEngine.status)
    private val _liveTest = MutableStateFlow(LiveTestConsoleMetrics())

    private val _geminiApiKey = MutableStateFlow(cloudGateway.geminiApiKey)
    private val _activeModel = MutableStateFlow(cloudGateway.activeModelName)
    private val _cloudStatus = MutableStateFlow<String?>(null)
    private val _isTestingCloud = MutableStateFlow(false)
    private val _notification = MutableStateFlow<String?>(null)

    private data class LocalEnginePrefs(
        val servingMode: ServingMode,
        val curatedCatalog: List<LocalServerModel>,
        val storageModels: List<LocalServerModel>,
        val isScanningStorage: Boolean,
        val activeModel: LocalServerModel,
        val status: LocalModelStatus,
        val liveTest: LiveTestConsoleMetrics
    )

    private val localEnginePrefsFlow = combine(
        combine(_servingMode, _curatedLocalModels, _storageModels, _isScanningStorage, _activeLocalModel) { mode, catalog, storage, scanning, active ->
            Quintet(mode, catalog, storage, scanning, active)
        },
        combine(_localModelStatus, _liveTest) { status, liveTest ->
            Pair(status, liveTest)
        }
    ) { q1, (status, liveTest) ->
        LocalEnginePrefs(
            servingMode = q1.first,
            curatedCatalog = q1.second,
            storageModels = q1.third,
            isScanningStorage = q1.fourth,
            activeModel = q1.fifth,
            status = status,
            liveTest = liveTest
        )
    }

    private data class Quintet<A, B, C, D, E>(val first: A, val second: B, val third: C, val fourth: D, val fifth: E)

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
            storageModels = subState.localPrefs.storageModels,
            isScanningStorage = subState.localPrefs.isScanningStorage,
            activeLocalModel = subState.localPrefs.activeModel,
            localModelStatus = subState.localPrefs.status,
            testPrompt = subState.localPrefs.liveTest.prompt,
            testResponse = subState.localPrefs.liveTest.response,
            isGeneratingTest = subState.localPrefs.liveTest.isGenerating,
            testLatencyMs = subState.localPrefs.liveTest.latencyMs,
            testTtftMs = subState.localPrefs.liveTest.ttftMs,
            testTokenCount = subState.localPrefs.liveTest.tokenCount,
            testTokensPerSecond = subState.localPrefs.liveTest.tokensPerSecond,
            testModelTested = subState.localPrefs.liveTest.modelTested,
            testEngineType = subState.localPrefs.liveTest.engineType,
            testMemoryUsageMb = subState.localPrefs.liveTest.memoryUsageMb,
            testStatus = subState.localPrefs.liveTest.status,
            geminiApiKey = subState.key,
            activeModel = subState.cloudPrefs.model,
            cloudConnectionStatus = subState.cloudPrefs.status,
            isTestingCloud = subState.cloudPrefs.testing,
            notificationMessage = subState.cloudPrefs.notification,
            thermalSnapshot = thermalManager.snapshot.value,
            isTlsEnabled = tlsManager.config.value.enabled,
            tlsFingerprint = tlsManager.config.value.fingerprint
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = AdminUiState(
            curatedLocalModels = localEngine.curatedCatalog,
            storageModels = localEngine.storageModels,
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


    fun refreshThermal() {
        thermalManager.refresh()
    }

    fun toggleTls() {
        tlsManager.toggleTls()
    }

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
                _storageModels.value = localEngine.storageModels.map {
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
            _storageModels.value = localEngine.storageModels.map {
                it.copy(isLoaded = false, isServing = false)
            }
            _notification.value = "Unloaded local model from memory."
        }
    }

    fun importModelFromUri(uri: Uri, context: Context) {
        viewModelScope.launch {
            try {
                val takeFlags: Int = Intent.FLAG_GRANT_READ_URI_PERMISSION
                context.contentResolver.takePersistableUriPermission(uri, takeFlags)
            } catch (_: Exception) {}

            val fileName = queryFileName(context, uri) ?: "imported_model.gguf"
            val fileSize = queryFileSize(context, uri)
            val filePath = uri.toString()

            val registered = localEngine.registerStorageModel(fileName, filePath, fileSize)
            _storageModels.value = localEngine.storageModels
            _notification.value = "Imported '${registered.name}' from storage. Loading..."

            loadLocalModel(registered.id)
        }
    }

    fun scanDeviceStorage(context: Context) {
        viewModelScope.launch {
            _isScanningStorage.value = true
            _notification.value = "Scanning phone storage for .gguf model files..."
            val discovered = withContext(Dispatchers.IO) {
                localEngine.scanStorageForModels(context)
            }
            _storageModels.value = localEngine.storageModels
            _isScanningStorage.value = false
            if (discovered.isNotEmpty()) {
                _notification.value = "Found ${discovered.size} model(s) in phone storage."
            } else {
                _notification.value = "No .gguf files found in standard folders. Use 'Pick Model File' to select manually."
            }
        }
    }

    fun removeStorageModel(modelId: String) {
        val removed = localEngine.removeStorageModel(modelId)
        if (removed) {
            _storageModels.value = localEngine.storageModels
            _activeLocalModel.value = localEngine.activeModel
            _localModelStatus.value = localEngine.status
            _notification.value = "Removed model from storage catalog."
        }
    }

    fun setTestPrompt(prompt: String) {
        _liveTest.value = _liveTest.value.copy(prompt = prompt)
    }

    fun clearTestResponse() {
        _liveTest.value = _liveTest.value.copy(
            response = null,
            latencyMs = 0L,
            ttftMs = 0L,
            tokenCount = 0,
            tokensPerSecond = 0.0,
            status = null
        )
    }

    fun runLocalModelTest() {
        viewModelScope.launch {
            val prompt = _liveTest.value.prompt.ifBlank { "Explain how on-device AI models stream layers efficiently on mobile hardware." }
            val activeMode = _servingMode.value
            val isLocal = activeMode == ServingMode.LOCAL_MODEL || (activeMode == ServingMode.HYBRID && !cloudGateway.isConfigured)

            val modelName = if (isLocal) localEngine.activeModel.name else cloudGateway.activeModelName
            val engineType = if (isLocal) "AirLLM Layer-Stream (Edge)" else "Google Gemini Gateway (Cloud)"
            val memFootprint = if (isLocal) localEngine.activeModel.memoryFootprintMb else 0L

            _liveTest.value = _liveTest.value.copy(
                isGenerating = true,
                response = "",
                latencyMs = 0L,
                ttftMs = 0L,
                tokenCount = 0,
                tokensPerSecond = 0.0,
                modelTested = modelName,
                engineType = engineType,
                memoryUsageMb = memFootprint,
                status = "STREAMING"
            )

            val start = System.currentTimeMillis()
            var firstTokenTime: Long? = null
            var count = 0

            try {
                if (isLocal) {
                    localEngine.generateCompletionStream(prompt = prompt).collect { token ->
                        val now = System.currentTimeMillis()
                        if (firstTokenTime == null) {
                            firstTokenTime = now
                            _liveTest.value = _liveTest.value.copy(ttftMs = now - start)
                        }
                        count++
                        val currentText = (_liveTest.value.response ?: "") + token
                        val genSec = (now - (firstTokenTime ?: start)) / 1000.0
                        val currentSpeed = if (genSec > 0.05) count / genSec else 0.0

                        _liveTest.value = _liveTest.value.copy(
                            response = currentText,
                            tokenCount = count,
                            tokensPerSecond = currentSpeed
                        )
                    }
                } else {
                    val fullResponse = cloudGateway.generateChatCompletion(prompt = prompt)
                    val ttft = System.currentTimeMillis() - start
                    _liveTest.value = _liveTest.value.copy(ttftMs = ttft)
                    val words = fullResponse.split(" ")
                    for ((idx, word) in words.withIndex()) {
                        count++
                        val piece = if (idx == 0) word else " $word"
                        val currentText = (_liveTest.value.response ?: "") + piece
                        _liveTest.value = _liveTest.value.copy(
                            response = currentText,
                            tokenCount = count
                        )
                        delay(10)
                    }
                }

                val totalDuration = System.currentTimeMillis() - start
                val genDuration = if (firstTokenTime != null) totalDuration - (firstTokenTime!! - start) else totalDuration
                val finalSec = genDuration / 1000.0
                val finalSpeed = if (finalSec > 0.05 && count > 0) ((count / finalSec) * 10).toInt() / 10.0 else 0.0

                _liveTest.value = _liveTest.value.copy(
                    isGenerating = false,
                    latencyMs = totalDuration,
                    tokensPerSecond = finalSpeed,
                    status = "200 OK"
                )
            } catch (e: Exception) {
                _liveTest.value = _liveTest.value.copy(
                    isGenerating = false,
                    status = "FAILED",
                    response = "Inference execution error: ${e.message}"
                )
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

    companion object {
        fun queryFileName(context: Context, uri: Uri): String? {
            var name: String? = null
            try {
                context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameIndex != -1 && cursor.moveToFirst()) {
                        name = cursor.getString(nameIndex)
                    }
                }
            } catch (_: Exception) {}
            return name
        }

        fun queryFileSize(context: Context, uri: Uri): Long {
            var size: Long = 0L
            try {
                context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (sizeIndex != -1 && cursor.moveToFirst()) {
                        size = cursor.getLong(sizeIndex)
                    }
                }
            } catch (_: Exception) {}
            return size
        }
    }
}
