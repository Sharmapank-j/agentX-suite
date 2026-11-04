package com.agentx.feature.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.agentx.core.agent.execution.ExecutionQueue
import com.agentx.core.agent.execution.ExecutionState
import com.agentx.core.agent.orchestrator.ClinicalSupervisor
import com.agentx.core.inference.ContextBudget
import com.agentx.core.inference.ContextManager
import com.agentx.core.inference.GenerationEvent
import com.agentx.core.inference.InferenceEngine
import com.agentx.core.inference.InferenceRequest
import com.agentx.core.inference.ModelConfig
import com.agentx.core.inference.ModelLifecycleState
import com.agentx.core.inference.ModelStatus
import com.agentx.data.repository.AgentRepository
import com.agentx.data.repository.ConversationRepository
import com.agentx.data.repository.ModelRepository
import com.agentx.domain.models.Agent
import com.agentx.domain.models.AgentRole
import com.agentx.domain.models.Conversation
import com.agentx.domain.models.ExecutionStatus
import com.agentx.domain.models.GGUFModel
import com.agentx.domain.models.Message
import com.agentx.domain.models.MessageRole
import com.agentx.domain.models.MessageStatus
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID

data class LiveInferenceMetrics(
    val isLive: Boolean = false,
    val tokensGenerated: Int = 0,
    val tokensPerSecond: Float = 0f,
    val elapsedSeconds: Float = 0f,
    val memoryUsageMb: Long = 0L,
    val engineType: String = "Local GGUF"
)

data class ChatUiState(
    val conversations: List<Conversation> = emptyList(),
    val currentConversation: Conversation? = null,
    val messages: List<Message> = emptyList(),
    val availableAgents: List<Agent> = emptyList(),
    val selectedAgent: Agent? = null,
    val inputText: String = "",
    val isStreaming: Boolean = false,
    val streamingAgentName: String? = null,
    val activeModel: GGUFModel? = null,
    val allModels: List<GGUFModel> = emptyList(),
    val modelStatus: ModelStatus = ModelStatus(ModelLifecycleState.NOT_LOADED),
    val activeExecutionState: ExecutionState? = null,
    val liveMetrics: LiveInferenceMetrics = LiveInferenceMetrics()
)

class ChatViewModel(
    private val conversationRepository: ConversationRepository,
    private val agentRepository: AgentRepository,
    private val modelRepository: ModelRepository,
    private val inferenceEngine: InferenceEngine,
    private val clinicalSupervisor: ClinicalSupervisor? = null,
    private val executionQueue: ExecutionQueue? = null
) : ViewModel() {

    private val _currentConversationId = MutableStateFlow<String?>(null)
    private val _selectedAgentId = MutableStateFlow<String?>("agent_supervisor")
    private val _inputText = MutableStateFlow("")
    private val _isStreaming = MutableStateFlow(false)
    private val _messages = MutableStateFlow<List<Message>>(emptyList())
    private val _modelStatus = MutableStateFlow(inferenceEngine.getModelStatus())
    private val _liveMetrics = MutableStateFlow(LiveInferenceMetrics())
    private var generationJob: Job? = null

    private val executionFlow: StateFlow<ExecutionState> =
        executionQueue?.state ?: MutableStateFlow(ExecutionState())

    private val conversationFlow = combine(
        conversationRepository.getAllConversations(),
        _currentConversationId,
        agentRepository.getAllAgents(),
        _selectedAgentId
    ) { conversations, currentId, agents, selectedAgentId ->
        val activeConv = conversations.find { it.id == currentId } ?: conversations.firstOrNull()
        val activeAgent = agents.find { it.id == selectedAgentId } ?: agents.firstOrNull()
        Triple(conversations, activeConv, Pair(agents, activeAgent))
    }

    private val modelFlow = combine(
        modelRepository.getActiveModel(),
        modelRepository.getAllModels(),
        _modelStatus
    ) { active, all, status ->
        Triple(active, all, status)
    }

    private val inputFlow = combine(_messages, _inputText, _isStreaming, _liveMetrics) { messages, inputText, isStreaming, metrics ->
        InputState(messages, inputText, isStreaming, metrics)
    }

    private data class InputState(
        val messages: List<Message>,
        val inputText: String,
        val isStreaming: Boolean,
        val metrics: LiveInferenceMetrics
    )

    val uiState: StateFlow<ChatUiState> = combine(
        conversationFlow,
        modelFlow,
        inputFlow,
        executionFlow
    ) { (conversations, activeConv, agentPair), (activeModel, allModels, modelStatus), inputState, execState ->
        ChatUiState(
            conversations = conversations,
            currentConversation = activeConv,
            messages = inputState.messages,
            availableAgents = agentPair.first,
            selectedAgent = agentPair.second,
            inputText = inputState.inputText,
            isStreaming = inputState.isStreaming,
            streamingAgentName = if (inputState.isStreaming) agentPair.second?.name else null,
            activeModel = activeModel,
            allModels = allModels,
            modelStatus = modelStatus,
            activeExecutionState = if (execState.status == ExecutionStatus.RUNNING) execState else null,
            liveMetrics = inputState.metrics
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = ChatUiState()
    )

    init {
        viewModelScope.launch {
            _currentConversationId.collect { convId ->
                if (convId != null) {
                    conversationRepository.getMessagesForConversation(convId).collect {
                        _messages.value = it
                    }
                } else {
                    _messages.value = emptyList()
                }
            }
        }

        // Keep model status synced
        viewModelScope.launch {
            modelRepository.getActiveModel().collect { activeModel ->
                if (activeModel != null && activeModel.filePath.isNotEmpty()) {
                    ensureModelLoaded(activeModel)
                }
                _modelStatus.value = inferenceEngine.getModelStatus()
            }
        }
    }

    fun onInputTextChanged(text: String) {
        _inputText.value = text
    }

    fun onAgentSelected(agent: Agent) {
        _selectedAgentId.value = agent.id
    }

    fun selectConversation(conversationId: String) {
        _currentConversationId.value = conversationId
    }

    fun createNewConversation(title: String = "New Conversation") {
        viewModelScope.launch {
            val newId = UUID.randomUUID().toString()
            val newConv = Conversation(
                id = newId,
                title = title,
                activeAgentId = _selectedAgentId.value
            )
            conversationRepository.createConversation(newConv)
            _currentConversationId.value = newId
        }
    }

    fun renameConversation(conversationId: String, newTitle: String) {
        if (newTitle.isBlank()) return
        viewModelScope.launch {
            val current = conversationRepository.getConversationById(conversationId)
            if (current != null) {
                conversationRepository.updateConversation(current.copy(title = newTitle.trim()))
            }
        }
    }

    fun deleteConversation(conversationId: String) {
        viewModelScope.launch {
            conversationRepository.deleteConversation(conversationId)
            if (_currentConversationId.value == conversationId) {
                val remaining = conversationRepository.getAllConversations().first()
                _currentConversationId.value = remaining.firstOrNull()?.id
            }
        }
    }

    fun clearCurrentConversation() {
        val convId = _currentConversationId.value ?: return
        viewModelScope.launch {
            conversationRepository.clearMessagesForConversation(convId)
        }
    }

    fun deleteMessage(messageId: String) {
        viewModelScope.launch {
            conversationRepository.deleteMessage(messageId)
        }
    }

    fun stopGeneration() {
        viewModelScope.launch {
            inferenceEngine.stopGeneration()
            generationJob?.cancel()
            _isStreaming.value = false
            _modelStatus.value = inferenceEngine.getModelStatus()
        }
    }

    fun switchActiveModel(modelId: String) {
        viewModelScope.launch {
            modelRepository.setActiveModel(modelId)
            val model = modelRepository.getModelById(modelId)
            if (model != null && model.filePath.isNotEmpty()) {
                ensureModelLoaded(model)
            }
            _modelStatus.value = inferenceEngine.getModelStatus()
        }
    }

    private suspend fun ensureModelLoaded(model: GGUFModel) {
        try {
            inferenceEngine.loadModel(
                ModelConfig(
                    modelId = model.id,
                    modelPath = model.filePath,
                    contextLength = model.contextLength,
                    threads = model.threads,
                    temperature = model.temperature,
                    topP = model.topP
                )
            )
            _modelStatus.value = inferenceEngine.getModelStatus()
        } catch (_: Exception) {
            _modelStatus.value = inferenceEngine.getModelStatus()
        }
    }

    fun sendPrompt(prompt: String) {
        _inputText.value = prompt
        sendMessage()
    }

    fun sendMessage() {
        val text = _inputText.value.trim()
        if (text.isEmpty() || _isStreaming.value) return

        val state = uiState.value
        val agent = state.selectedAgent
        val agentName = agent?.name ?: "Chief Orchestrator"

        generationJob?.cancel()
        generationJob = viewModelScope.launch {
            var convId = state.currentConversation?.id
            if (convId == null) {
                convId = UUID.randomUUID().toString()
                conversationRepository.createConversation(
                    Conversation(
                        id = convId,
                        title = text.take(30),
                        activeAgentId = agent?.id
                    )
                )
                _currentConversationId.value = convId
            }

            val userMsg = Message(
                id = UUID.randomUUID().toString(),
                conversationId = convId,
                role = MessageRole.USER,
                senderName = "User",
                content = text,
                status = MessageStatus.COMPLETED
            )
            conversationRepository.sendMessage(userMsg)
            _inputText.value = ""

            val isSupervisor = agent == null || agent.role == AgentRole.SUPERVISOR || agent.id == "agent_supervisor"
            if (isSupervisor && clinicalSupervisor != null) {
                executeMultiAgentSupervisor(
                    convId = convId,
                    userPrompt = text,
                    agentName = agentName
                )
            } else {
                executeInference(
                    convId = convId,
                    userPrompt = text,
                    agent = agent,
                    agentName = agentName
                )
            }
        }
    }

    fun regenerateResponse(assistantMessageId: String) {
        if (_isStreaming.value) return
        val currentMessages = _messages.value
        val targetIndex = currentMessages.indexOfFirst { it.id == assistantMessageId }
        if (targetIndex == -1) return

        // Find the preceding user query
        val userQuery = currentMessages.take(targetIndex).lastOrNull { it.role == MessageRole.USER }?.content
            ?: return

        val convId = _currentConversationId.value ?: return
        val agent = uiState.value.selectedAgent
        val agentName = agent?.name ?: "Chief Orchestrator"

        generationJob?.cancel()
        generationJob = viewModelScope.launch {
            // Delete the old assistant response
            conversationRepository.deleteMessage(assistantMessageId)

            val isSupervisor = agent == null || agent.role == AgentRole.SUPERVISOR || agent.id == "agent_supervisor"
            if (isSupervisor && clinicalSupervisor != null) {
                executeMultiAgentSupervisor(
                    convId = convId,
                    userPrompt = userQuery,
                    agentName = agentName
                )
            } else {
                executeInference(
                    convId = convId,
                    userPrompt = userQuery,
                    agent = agent,
                    agentName = agentName
                )
            }
        }
    }

    private suspend fun executeMultiAgentSupervisor(
        convId: String,
        userPrompt: String,
        agentName: String
    ) {
        val assistantMsgId = UUID.randomUUID().toString()
        var assistantMsg = Message(
            id = assistantMsgId,
            conversationId = convId,
            role = MessageRole.ASSISTANT,
            senderName = "$agentName (Multi-Agent Team)",
            content = "⚡ Multi-agent intelligence pipeline active...\nDecomposing inquiry into specialized subtasks.",
            agentId = "agent_supervisor",
            status = MessageStatus.STREAMING
        )
        conversationRepository.sendMessage(assistantMsg)
        _isStreaming.value = true

        // Ensure active model is loaded in memory
        try {
            val activeModel = modelRepository.getActiveModel().first()
            if (activeModel != null && activeModel.filePath.isNotEmpty()) {
                ensureModelLoaded(activeModel)
            } else if (inferenceEngine.getModelStatus().state == ModelLifecycleState.NOT_LOADED) {
                inferenceEngine.loadModel(ModelConfig(modelId = "model_default", modelPath = ""))
            }
        } catch (_: Exception) { }

        val progressJob = executionQueue?.let { queue ->
            viewModelScope.launch {
                queue.state.collect { state ->
                    if (state.status == ExecutionStatus.RUNNING) {
                        val progressHeader = "⚡ **Multi-Agent Pipeline Active** (Step ${state.currentStep}/${state.totalSteps})\n"
                        val activityText = "• **Current Activity**: ${state.currentActivity}\n"
                        val intermediate = if (state.intermediateOutputs.isNotEmpty()) {
                            "\n**Completed Specialist Findings:**\n" + state.intermediateOutputs.joinToString("\n") { "  - $it" }
                        } else ""
                        assistantMsg = assistantMsg.copy(
                            content = progressHeader + activityText + intermediate,
                            status = MessageStatus.STREAMING
                        )
                        conversationRepository.updateMessage(assistantMsg)
                    }
                }
            }
        }

        try {
            val coordResult = clinicalSupervisor?.coordinate(
                userGoal = userPrompt,
                conversationId = convId
            )
            progressJob?.cancel()

            if (coordResult != null && coordResult.output.isNotBlank() && coordResult.output != "[No output generated]") {
                assistantMsg = assistantMsg.copy(
                    content = coordResult.output,
                    tokenCount = coordResult.tokenUsage,
                    status = MessageStatus.COMPLETED
                )
                conversationRepository.updateMessage(assistantMsg)
            } else {
                executeInference(convId, userPrompt, null, agentName)
            }
        } catch (e: Exception) {
            progressJob?.cancel()
            assistantMsg = assistantMsg.copy(
                content = "Error during multi-agent coordination: ${e.message}",
                status = MessageStatus.ERROR
            )
            conversationRepository.updateMessage(assistantMsg)
        } finally {
            _isStreaming.value = false
            _modelStatus.value = inferenceEngine.getModelStatus()
        }
    }

    private suspend fun executeInference(
        convId: String,
        userPrompt: String,
        agent: Agent?,
        agentName: String
    ) {
        val assistantMsgId = UUID.randomUUID().toString()
        var assistantMsg = Message(
            id = assistantMsgId,
            conversationId = convId,
            role = MessageRole.ASSISTANT,
            senderName = agentName,
            content = "",
            agentId = agent?.id,
            status = MessageStatus.STREAMING
        )
        conversationRepository.sendMessage(assistantMsg)
        _isStreaming.value = true

        // Ensure active model is loaded in memory
        try {
            val activeModel = modelRepository.getActiveModel().first()
            if (activeModel != null && activeModel.filePath.isNotEmpty()) {
                ensureModelLoaded(activeModel)
            } else if (inferenceEngine.getModelStatus().state == ModelLifecycleState.NOT_LOADED) {
                inferenceEngine.loadModel(ModelConfig(modelId = "model_default", modelPath = ""))
            }
        } catch (_: Exception) { }

        // Context Budgeting & Sliding Window Construction
        val historyMessages = conversationRepository.getMessagesForConversation(convId).first()
            .filter { it.id != assistantMsgId }

        val activeModelConfig = modelRepository.getActiveModel().first()
        val maxContext = activeModelConfig?.contextLength ?: 2048

        val contextualPrompt = ContextManager.buildPromptWithContext(
            systemPrompt = agent?.systemPrompt,
            history = historyMessages,
            currentPrompt = userPrompt,
            budget = ContextBudget(
                maxContextTokens = maxContext,
                maxOutputTokens = agent?.maxTokens ?: 512
            )
        )

        val buffer = StringBuilder()
        val startTime = System.currentTimeMillis()
        var tokenCount = 0

        _liveMetrics.value = LiveInferenceMetrics(
            isLive = true,
            tokensGenerated = 0,
            tokensPerSecond = 0f,
            elapsedSeconds = 0f,
            memoryUsageMb = inferenceEngine.getModelStatus().memoryUsageBytes / (1024 * 1024)
        )

        inferenceEngine.generate(
            InferenceRequest(
                prompt = contextualPrompt,
                maxTokens = agent?.maxTokens ?: 512,
                temperature = agent?.temperature ?: 0.7f,
                topP = agent?.topP ?: 0.9f
            )
        ).collect { event ->
            when (event) {
                is GenerationEvent.Token -> {
                    buffer.append(event.text)
                    tokenCount++
                    val elapsedMs = (System.currentTimeMillis() - startTime).coerceAtLeast(1)
                    val tps = (tokenCount * 1000f) / elapsedMs
                    _liveMetrics.value = LiveInferenceMetrics(
                        isLive = true,
                        tokensGenerated = tokenCount,
                        tokensPerSecond = tps,
                        elapsedSeconds = elapsedMs / 1000f,
                        memoryUsageMb = inferenceEngine.getModelStatus().memoryUsageBytes / (1024 * 1024)
                    )
                    assistantMsg = assistantMsg.copy(
                        content = buffer.toString(),
                        tokenCount = tokenCount,
                        status = MessageStatus.STREAMING
                    )
                    conversationRepository.updateMessage(assistantMsg)
                }
                is GenerationEvent.Finished -> {
                    val elapsedMs = (System.currentTimeMillis() - startTime).coerceAtLeast(1)
                    val finalTps = (event.completionTokens.coerceAtLeast(tokenCount) * 1000f) / elapsedMs
                    _liveMetrics.value = _liveMetrics.value.copy(
                        isLive = false,
                        tokensGenerated = event.totalTokens,
                        tokensPerSecond = finalTps,
                        elapsedSeconds = elapsedMs / 1000f
                    )
                    assistantMsg = assistantMsg.copy(
                        content = buffer.toString(),
                        tokenCount = event.totalTokens,
                        status = MessageStatus.COMPLETED
                    )
                    conversationRepository.updateMessage(assistantMsg)
                    _isStreaming.value = false
                    _modelStatus.value = inferenceEngine.getModelStatus()
                }
                is GenerationEvent.Error -> {
                    _liveMetrics.value = _liveMetrics.value.copy(isLive = false)
                    val currentText = buffer.toString()
                    assistantMsg = assistantMsg.copy(
                        content = if (currentText.isNotEmpty()) currentText else "Error during generation: ${event.message}",
                        status = MessageStatus.ERROR
                    )
                    conversationRepository.updateMessage(assistantMsg)
                    _isStreaming.value = false
                    _modelStatus.value = inferenceEngine.getModelStatus()
                }
            }
        }
    }
}
