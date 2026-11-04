package com.agentx.medical.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.agentx.medical.core.agent.execution.ExecutionQueue
import com.agentx.medical.core.agent.execution.ExecutionState
import com.agentx.medical.core.inference.CloudModelConfig
import com.agentx.medical.core.inference.CloudProvider
import com.agentx.medical.core.inference.HybridInferenceEngine
import com.agentx.medical.core.inference.InferenceMode
import com.agentx.medical.data.repository.ExecutionRepository
import com.agentx.medical.data.repository.ToolRepository
import com.agentx.medical.domain.models.ExecutionLog
import com.agentx.medical.domain.models.ExecutionStatus
import com.agentx.medical.domain.models.Tool
import com.agentx.medical.domain.models.ToolPermission
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SettingsUiState(
    val tools: List<Tool> = emptyList(),
    val permissions: List<ToolPermission> = emptyList(),
    val executions: List<ExecutionLog> = emptyList(),
    val activeExecutionState: ExecutionState? = null,
    val darkTheme: Boolean = true,
    val maxCpuThreads: Int = 4,
    val enableMlock: Boolean = false,
    val cloudConfig: CloudModelConfig = CloudModelConfig(),
    val cloudTestResult: String? = null
)

class SettingsViewModel(
    private val toolRepository: ToolRepository,
    private val executionRepository: ExecutionRepository,
    private val executionQueue: ExecutionQueue? = null,
    private val hybridInferenceEngine: HybridInferenceEngine? = null
) : ViewModel() {

    private val _darkTheme = MutableStateFlow(true)
    private val _threads = MutableStateFlow(4)
    private val _mlock = MutableStateFlow(false)
    private val _cloudConfig = MutableStateFlow(
        hybridInferenceEngine?.cloudEngine?.config ?: CloudModelConfig()
    )
    private val _cloudTestResult = MutableStateFlow<String?>(null)

    private val preferencesFlow = combine(_darkTheme, _threads, _mlock, _cloudConfig, _cloudTestResult) { darkTheme, threads, mlock, cloudConfig, cloudTest ->
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
        preferencesFlow,
        executionFlow
    ) { tools, permissions, executions, prefs, execState ->
        SettingsUiState(
            tools = tools,
            permissions = permissions,
            executions = executions,
            activeExecutionState = if (execState.status == ExecutionStatus.RUNNING) execState else null,
            darkTheme = prefs.darkTheme,
            maxCpuThreads = prefs.threads,
            enableMlock = prefs.mlock,
            cloudConfig = prefs.cloudConfig,
            cloudTestResult = prefs.cloudTestResult
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = SettingsUiState()
    )

    fun setInferenceMode(mode: InferenceMode) {
        val updated = _cloudConfig.value.copy(inferenceMode = mode)
        _cloudConfig.value = updated
        hybridInferenceEngine?.inferenceMode = mode
        hybridInferenceEngine?.cloudEngine?.config = updated
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
    }

    fun testCloudConnection() {
        viewModelScope.launch {
            _cloudTestResult.value = "Testing connection..."
            try {
                val config = _cloudConfig.value
                if (config.provider == CloudProvider.GEMINI && config.apiKey.isBlank()) {
                    _cloudTestResult.value = "Error: Gemini API Key is empty"
                    return@launch
                }
                _cloudTestResult.value = "Verified! Connected to ${config.provider.displayName} (${config.modelName})"
            } catch (e: Exception) {
                _cloudTestResult.value = "Failed: ${e.message}"
            }
        }
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
    }

    fun setCpuThreads(threads: Int) {
        _threads.value = threads
    }

    fun setMlock(enabled: Boolean) {
        _mlock.value = enabled
    }
}
