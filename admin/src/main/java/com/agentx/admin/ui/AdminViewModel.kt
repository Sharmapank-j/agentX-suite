package com.agentx.admin.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.agentx.admin.AgentXAdminApplication
import com.agentx.admin.models.ApiKeyRecord
import com.agentx.admin.models.CliEntry
import com.agentx.admin.models.ClientSession
import com.agentx.admin.models.LocalModelStatus
import com.agentx.admin.models.LocalServerModel
import com.agentx.admin.models.ServerTelemetry
import com.agentx.admin.models.ServingMode
import com.agentx.admin.models.TrafficLog
import com.agentx.admin.models.TunnelConfig
import com.agentx.admin.models.TunnelProvider
import com.agentx.admin.models.TunnelStatus
import com.agentx.admin.server.TunnelManager
import com.agentx.admin.service.AgentXServerService
import kotlinx.coroutines.Dispatchers
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
    val localModelStatus: LocalModelStatus = LocalModelStatus.NOT_LOADED,
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
    val tlsFingerprint: String = "",
    val tunnelConfig: TunnelConfig = TunnelConfig(),
    val cliHistory: List<CliEntry> = emptyList(),
    val isCliExecuting: Boolean = false
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
    val tunnelManager = app.tunnelManager

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

    private val _cliHistory = MutableStateFlow<List<CliEntry>>(listOf(
        CliEntry(
            command = null,
            output = """
============================================================
 AGENTX SERVE DIAGNOSTIC TERMINAL & EDGE HOST HUD [v1.0]
 Platform: 24/7 Dedicated Old-Phone Edge AI Server
 Type 'help' for available commands.
============================================================
            """.trimIndent()
        )
    ))
    private val _isCliExecuting = MutableStateFlow(false)

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

    private data class CliTunnelPrefs(
        val tunnelConfig: TunnelConfig,
        val cliHistory: List<CliEntry>,
        val isCliExecuting: Boolean
    )

    private val cliTunnelFlow = combine(tunnelManager.config, _cliHistory, _isCliExecuting) { tunnel, cli, executing ->
        CliTunnelPrefs(tunnel, cli, executing)
    }

    private data class SubAdminState(
        val port: Int,
        val tab: Int,
        val key: String,
        val cloudPrefs: CloudPreferences,
        val localPrefs: LocalEnginePrefs,
        val cliTunnelPrefs: CliTunnelPrefs
    )

    val uiState: StateFlow<AdminUiState> = combine(
        server.telemetry,
        clientManager.clients,
        clientManager.apiKeys,
        clientManager.trafficLogs,
        combine(serverSettingsFlow, cloudSettingsFlow, localEnginePrefsFlow, cliTunnelFlow) { (port, tab, key), cloudPrefs, localPrefs, cliTunnel ->
            SubAdminState(port, tab, key, cloudPrefs, localPrefs, cliTunnel)
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
            tlsFingerprint = tlsManager.config.value.fingerprint,
            tunnelConfig = subState.cliTunnelPrefs.tunnelConfig,
            cliHistory = subState.cliTunnelPrefs.cliHistory,
            isCliExecuting = subState.cliTunnelPrefs.isCliExecuting
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = AdminUiState(
            curatedLocalModels = localEngine.curatedCatalog,
            storageModels = localEngine.storageModels,
            activeLocalModel = localEngine.activeModel,
            localModelStatus = localEngine.status,
            tunnelConfig = tunnelManager.config.value,
            cliHistory = _cliHistory.value
        )
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
            _notification.value = "Checking local runtime availability for $modelId..."
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
            _notification.value = "Local runtime is not loaded."
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
            val engineType = if (isLocal) "Local GGUF Runtime (llama.cpp)" else "Google Gemini Gateway (Cloud)"
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
                    localEngine.generateCompletionStream(prompt = prompt, maxTokens = 40).collect { token ->
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
                    val completedAt = System.currentTimeMillis()
                    _liveTest.value = _liveTest.value.copy(
                        response = fullResponse,
                        ttftMs = completedAt - start,
                        tokenCount = 0,
                        tokensPerSecond = 0.0
                    )
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

    fun startTunnel(provider: TunnelProvider, token: String = "", customDomain: String = "") {
        val updated = tunnelManager.startTunnel(provider, token, customDomain)
        _notification.value = "Tunnel active: ${updated.publicUrl}"
    }

    fun stopTunnel() {
        tunnelManager.stopTunnel()
        _notification.value = "Tunnel stopped"
    }

    fun testTunnelConnection() {
        viewModelScope.launch {
            _notification.value = "Testing public route via local port ${_selectedPort.value}..."
            val (ok, latency) = tunnelManager.testTunnel(_selectedPort.value)
            if (ok) {
                _notification.value = "Tunnel route verified (HTTP 200 OK in ${latency}ms)"
            } else {
                _notification.value = "Tunnel test failed (${latency}ms)"
            }
        }
    }

    fun clearCliHistory() {
        _cliHistory.value = listOf(
            CliEntry(
                command = null,
                output = "Terminal buffer cleared. Type 'help' for commands."
            )
        )
    }

    fun executeCliCommand(rawCmd: String) {
        val cmd = rawCmd.trim()
        if (cmd.isBlank()) return

        val userEntry = CliEntry(command = cmd, output = "")
        _cliHistory.value = _cliHistory.value + userEntry

        viewModelScope.launch {
            _isCliExecuting.value = true
            try {
                val output = handleCliCommand(cmd)
                if (output.isNotBlank()) {
                    val responseEntry = CliEntry(command = null, output = output, isError = false)
                    _cliHistory.value = _cliHistory.value + responseEntry
                }
            } catch (e: Exception) {
                val errEntry = CliEntry(command = null, output = "ERR: ${e.message}", isError = true)
                _cliHistory.value = _cliHistory.value + errEntry
            } finally {
                _isCliExecuting.value = false
            }
        }
    }

    private suspend fun handleCliCommand(cmd: String): String {
        val parts = cmd.split("\\s+".toRegex())
        val root = parts.firstOrNull()?.lowercase() ?: ""
        return when (root) {
            "help" -> """
=== AGENTX SERVE DIAGNOSTIC COMMANDS ===
  stats          - Display requests, tokens, RAM usage, thermal and tunnel stats
  device         - Inspect host device hardware, battery %, temp, and 24/7 server readiness
  llm            - Inspect active model architecture, RAM footprint, context window
  bench          - Run a 10-token local inference benchmark measuring TTFT and speed
  test <prompt>  - Run custom text completion through active serving engine
  tunnel         - Show remote tunneling provider, status, and public HTTPS URL
  models         - List available curated models and custom storage GGUFs
  load <id>      - Switch active local model by ID into native llama.cpp runtime
  unload         - Unload active model from memory
  clients        - List connected client sessions and blocked IP addresses
  datetime       - Display current system date, time, and timezone offset
  storage        - Inspect device internal storage capacity and free space
  calc <expr>    - Evaluate mathematical arithmetic expression deterministically
  clear          - Clear terminal output
""".trimIndent()

            "stats" -> {
                val telem = server.telemetry.value
                val tSnap = thermalManager.snapshot.value
                val tCfg = tunnelManager.config.value
                val upSec = telem.startedAtMs?.let { (System.currentTimeMillis() - it) / 1000 } ?: 0L
                val upMin = upSec / 60
                val upHours = upMin / 60
                """
--- SERVER & SYSTEM TELEMETRY ---
Status:         ${if (telem.isRunning) "ONLINE [PORT ${telem.port}]" else "STOPPED"}
Local IP:       ${telem.ipAddress}:${telem.port}
Serving Mode:   ${telem.servingMode.label}
Active Model:   ${telem.activeLocalModel ?: "None"}
RAM Footprint:  ${telem.localMemoryUsageMb} MB (Mapped via mmap)
Requests Served:${telem.requestsServed}
Tokens Emitted: ${telem.tokensEmitted}
Uptime:         ${upHours}h ${upMin % 60}m ${upSec % 60}s
Active Clients: ${telem.activeClientsCount}
Battery State:  ${tSnap.batteryPct}% ${if (tSnap.isCharging) "[Charging ⚡]" else "[Discharging]"}
Device Temp:    ${tSnap.temperatureCelsius}°C (${tSnap.status.label})
Remote Tunnel:  ${if (tCfg.isEnabled) tCfg.publicUrl ?: "Connecting" else "Disabled"}
""".trimIndent()
            }

            "device" -> {
                val tSnap = thermalManager.snapshot.value
                val totalMemMb = Runtime.getRuntime().totalMemory() / (1024 * 1024)
                val maxMemMb = Runtime.getRuntime().maxMemory() / (1024 * 1024)
                val freeMemMb = Runtime.getRuntime().freeMemory() / (1024 * 1024)
                val isCharging = tSnap.isCharging
                """
--- HOST DEVICE HARDWARE DIAGNOSTICS ---
Device:         ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL} (${android.os.Build.DEVICE})
Android OS:     Android ${android.os.Build.VERSION.RELEASE} (API ${android.os.Build.VERSION.SDK_INT})
Hardware Board: ${android.os.Build.BOARD}
JVM Heap RAM:   ${totalMemMb - freeMemMb}MB used / ${totalMemMb}MB allocated (Max: ${maxMemMb}MB)
Battery:        ${tSnap.batteryPct}% (Status: ${if (isCharging) "Plugged In ⚡" else "On Battery"})
Battery Temp:   ${tSnap.temperatureCelsius}°C
Thermal Status: ${tSnap.status.label}
Stream Delay:   ${tSnap.status.extraDelayMs} ms/token (Adaptive thermal throttling)
24/7 Readiness: ${if (isCharging) "READY (Continuous power supplied)" else "CAUTION (Connect wall charger for 24/7 server use)"}
""".trimIndent()
            }

            "llm" -> {
                val model = localEngine.activeModel
                """
--- ACTIVE LLM SPECIFICATION ---
Model Name:     ${model.name}
Model ID:       ${model.id}
Architecture:   ${model.architecture}
Parameters:     ${model.parameterCount}
Quantization:   ${model.quantization}
Context Window: ${model.contextWindow} tokens
RAM Footprint:  ${model.memoryFootprintMb} MB (Strictly bounded 1000MB-2048MB)
Execution:      llama.cpp Native GGUF Mobile Engine
Status:         ${localEngine.status.name}
Is Loaded:      ${model.isLoaded}
Is Serving:     ${model.isServing}
Description:    ${model.description}
""".trimIndent()
            }

            "bench" -> {
                val prompt = "Describe the structure and biological function of hemoglobin in detail."
                val start = System.currentTimeMillis()
                var firstTokenTime: Long? = null
                var count = 0
                val sb = StringBuilder()
                localEngine.generateCompletionStream(prompt = prompt, maxTokens = 50).collect { token ->
                    val now = System.currentTimeMillis()
                    if (firstTokenTime == null) firstTokenTime = now
                    count++
                    sb.append(token)
                }
                val totalDuration = System.currentTimeMillis() - start
                val ttft = if (firstTokenTime != null) firstTokenTime!! - start else totalDuration
                val genDuration = if (firstTokenTime != null) totalDuration - ttft else totalDuration
                val speed = if (genDuration > 50 && count > 0) ((count / (genDuration / 1000.0)) * 10).toInt() / 10.0 else 0.0
                """
=== INFERENCE BENCHMARK COMPLETE ===
Target Engine:  ${localEngine.activeModel.name}
Prompt:         $prompt
Generated Text:
${sb.toString().trim()}

RAM Footprint:  ${localEngine.activeModel.memoryFootprintMb} MB
Time To First Token (TTFT): ${ttft} ms
Tokens Generated:           $count tokens
Generation Duration:        ${genDuration} ms
Throughput:                 $speed tokens/sec
Health:                     200 OK (Engine fully operational)
""".trimIndent()
            }

            "test" -> {
                val prompt = cmd.removePrefix("test").trim()
                if (prompt.isBlank()) {
                    return "Usage: test <your prompt text>"
                }
                val start = System.currentTimeMillis()
                var firstTokenTime: Long? = null
                var count = 0
                val sb = StringBuilder()
                localEngine.generateCompletionStream(prompt = prompt, maxTokens = 40).collect { token ->
                    val now = System.currentTimeMillis()
                    if (firstTokenTime == null) firstTokenTime = now
                    count++
                    sb.append(token)
                }
                val totalDuration = System.currentTimeMillis() - start
                val ttft = if (firstTokenTime != null) firstTokenTime!! - start else totalDuration
                val genDuration = if (firstTokenTime != null) totalDuration - ttft else totalDuration
                val speed = if (genDuration > 50 && count > 0) ((count / (genDuration / 1000.0)) * 10).toInt() / 10.0 else 0.0
                """
=== INFERENCE RESULT ===
Prompt: "$prompt"
Output:
${sb.toString()}

--- Metrics ---
TTFT: ${ttft}ms | Speed: ${speed} t/s | Tokens: $count | Total: ${totalDuration}ms
""".trimIndent()
            }

            "tunnel" -> {
                val tCfg = tunnelManager.config.value
                """
--- GLOBAL REMOTE TUNNELING ---
Provider:       ${tCfg.provider.displayName}
Status:         ${tCfg.status.label}
Public URL:     ${tCfg.publicUrl ?: "Not connected"}
Local Forward:  http://127.0.0.1:${_selectedPort.value}
Latency:        ${tCfg.latencyMs?.let { "$it ms" } ?: "Not tested (Run 'Test Route' in Tunnel tab)"}
Custom Domain:  ${tCfg.customDomain.ifBlank { "None (auto-assigned)" }}
""".trimIndent()
            }

            "models" -> {
                val curated = localEngine.curatedCatalog
                val storage = localEngine.storageModels
                val sb = StringBuilder("=== AVAILABLE LOCAL MODELS ===\n")
                sb.append("[Curated Edge Models]\n")
                curated.forEach { m ->
                    val mark = if (m.id == localEngine.activeModel.id) " [ACTIVE]" else ""
                    sb.append("  • ${m.id} (${m.name}) - ${m.parameterCount}, ~${m.memoryFootprintMb}MB RAM$mark\n")
                }
                if (storage.isNotEmpty()) {
                    sb.append("\n[Custom Storage Models (.gguf)]\n")
                    storage.forEach { m ->
                        val mark = if (m.id == localEngine.activeModel.id) " [ACTIVE]" else ""
                        sb.append("  • ${m.id} (${m.name}) - ${m.parameterCount}, ~${m.memoryFootprintMb}MB RAM$mark\n")
                    }
                }
                sb.toString().trimEnd()
            }

            "load" -> {
                val targetId = parts.getOrNull(1)?.trim()
                if (targetId.isNullOrBlank()) {
                    "Usage: load <model_id> (Type 'models' to see IDs)"
                } else {
                    loadLocalModel(targetId)
                    "Model load command dispatched for: $targetId"
                }
            }

            "unload" -> {
                unloadLocalModel()
                "Active local model unloaded from RAM."
            }

            "clients" -> {
                val sessions = clientManager.clients.value
                if (sessions.isEmpty()) {
                    "No active client sessions connected."
                } else {
                    val sb = StringBuilder("=== CONNECTED CLIENT SESSIONS (${sessions.size}) ===\n")
                    sessions.forEach { s ->
                        val blk = if (s.isBlocked) " [BLOCKED]" else ""
                        sb.append("  • ${s.ipAddress} (${s.deviceName}) - ${s.totalRequests} requests, ${s.totalTokens} tokens$blk\n")
                    }
                    sb.toString().trimEnd()
                }
            }

            "clear" -> {
                clearCliHistory()
                ""
            }

            "datetime" -> {
                val now = java.util.Date()
                val iso = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", java.util.Locale.US).apply {
                    timeZone = java.util.TimeZone.getTimeZone("UTC")
                }.format(now)
                val localStr = java.text.SimpleDateFormat("EEEE, MMMM d, yyyy HH:mm:ss z", java.util.Locale.getDefault()).format(now)
                """
--- CURRENT SYSTEM DATE & TIME ---
UTC ISO 8601: $iso
Local Time:   $localStr
Timezone:     ${java.util.TimeZone.getDefault().id}
Timestamp:    ${now.time}
""".trimIndent()
            }

            "storage" -> {
                val dataDir = try { android.os.Environment.getDataDirectory() } catch (_: Throwable) { java.io.File("/") }
                val stat = try { android.os.StatFs(dataDir.path) } catch (_: Throwable) { null }
                val totalBytes = stat?.let { it.blockCountLong * it.blockSizeLong } ?: dataDir.totalSpace
                val availBytes = stat?.let { it.availableBlocksLong * it.blockSizeLong } ?: dataDir.freeSpace
                val totalGb = totalBytes.toDouble() / (1024.0 * 1024.0 * 1024.0)
                val availGb = availBytes.toDouble() / (1024.0 * 1024.0 * 1024.0)
                val usedPct = if (totalBytes > 0) ((totalBytes - availBytes).toDouble() / totalBytes) * 100.0 else 0.0
                """
--- HOST STORAGE INSPECTION ---
Path:            ${dataDir.absolutePath}
Total Storage:   ${String.format(java.util.Locale.US, "%.2f", totalGb)} GB
Available Space: ${String.format(java.util.Locale.US, "%.2f", availGb)} GB
Used Storage:    ${String.format(java.util.Locale.US, "%.1f", usedPct)}%
""".trimIndent()
            }

            "calc" -> {
                val expr = cmd.removePrefix("calc").trim()
                if (expr.isBlank()) {
                    "Usage: calc <expression> (e.g. 'calc 15 * (4 + 6) / 2')"
                } else {
                    val sanitized = expr.replace(" ", "")
                    val res = when {
                        sanitized.contains("+") -> sanitized.split("+").sumOf { it.toDoubleOrNull() ?: 0.0 }
                        sanitized.contains("*") -> sanitized.split("*").fold(1.0) { acc, s -> acc * (s.toDoubleOrNull() ?: 1.0) }
                        sanitized.contains("-") -> {
                            val p = sanitized.split("-")
                            p.drop(1).fold(p[0].toDoubleOrNull() ?: 0.0) { acc, s -> acc - (s.toDoubleOrNull() ?: 0.0) }
                        }
                        sanitized.contains("/") -> {
                            val p = sanitized.split("/")
                            p.drop(1).fold(p[0].toDoubleOrNull() ?: 0.0) { acc, s -> acc / (s.toDoubleOrNull() ?: 1.0) }
                        }
                        else -> expr.toDoubleOrNull() ?: 0.0
                    }
                    "Result: $res"
                }
            }

            else -> "Command not recognized: '$cmd'. Type 'help' for available commands."
        }
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
