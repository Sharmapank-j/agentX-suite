package com.agentx.medical.feature.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.agentx.medical.core.agent.execution.ExecutionQueue
import com.agentx.medical.core.agent.execution.ExecutionState
import com.agentx.medical.core.agent.orchestrator.ClinicalSupervisor
import com.agentx.medical.core.inference.ContextBudget
import com.agentx.medical.core.inference.ContextManager
import com.agentx.medical.core.inference.GenerationEvent
import com.agentx.medical.core.inference.InferenceEngine
import com.agentx.medical.core.inference.InferenceRequest
import com.agentx.medical.core.inference.ModelConfig
import com.agentx.medical.core.inference.ModelLifecycleState
import com.agentx.medical.core.inference.ModelStatus
import com.agentx.medical.data.repository.AgentRepository
import com.agentx.medical.data.repository.ConversationRepository
import com.agentx.medical.data.repository.ModelRepository
import com.agentx.medical.domain.models.Agent
import com.agentx.medical.domain.models.AgentRole
import com.agentx.medical.domain.models.Conversation
import com.agentx.medical.domain.models.ExecutionStatus
import com.agentx.medical.domain.models.GGUFModel
import com.agentx.medical.domain.models.Message
import com.agentx.medical.domain.models.MessageRole
import com.agentx.medical.domain.models.MessageStatus
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import com.agentx.core.inference.model.ExtendedModelConfig
import com.agentx.medical.data.local.SettingsPreferencesManager
import com.agentx.medical.intelligence.AnswerDepth
import com.agentx.medical.intelligence.MedicalPromptBuilder
import com.agentx.medical.intelligence.MedicalQueryClassification
import com.agentx.medical.intelligence.MedicalQueryClassifier
import com.agentx.medical.intelligence.ResponseStyle
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
    val liveMetrics: LiveInferenceMetrics = LiveInferenceMetrics(),
    val currentResponseStyle: ResponseStyle = ResponseStyle.DEFAULT,
    val currentAnswerDepth: AnswerDepth = AnswerDepth.DEFAULT,
    val lastQueryClassification: MedicalQueryClassification? = null
)

class ChatViewModel(
    private val conversationRepository: ConversationRepository,
    private val agentRepository: AgentRepository,
    private val modelRepository: ModelRepository,
    private val inferenceEngine: InferenceEngine,
    private val clinicalSupervisor: ClinicalSupervisor? = null,
    private val executionQueue: ExecutionQueue? = null,
    private val preferencesManager: SettingsPreferencesManager? = null
) : ViewModel() {

    private val _currentConversationId = MutableStateFlow<String?>(null)
    private val _selectedAgentId = MutableStateFlow<String?>("agent_assistant")
    private val _inputText = MutableStateFlow("")
    private val _isStreaming = MutableStateFlow(false)
    private val _messages = MutableStateFlow<List<Message>>(emptyList())
    private val _modelStatus = MutableStateFlow(inferenceEngine.getModelStatus())
    private val _liveMetrics = MutableStateFlow(LiveInferenceMetrics())
    private val _lastClassification = MutableStateFlow<MedicalQueryClassification?>(null)
    private val _selectedResponseStyle = MutableStateFlow<ResponseStyle?>(null)
    private val _selectedAnswerDepth = MutableStateFlow<AnswerDepth?>(null)
    private var generationJob: Job? = null
    private var messagesJob: Job? = null

    private val executionFlow: StateFlow<ExecutionState> =
        executionQueue?.state ?: MutableStateFlow(ExecutionState())

    private val conversationFlow = combine(
        conversationRepository.getAllConversations(),
        _currentConversationId,
        agentRepository.getAllAgents(),
        _selectedAgentId
    ) { conversations, currentId, agents, selectedAgentId ->
        val activeConv = if (currentId != null) {
            conversations.find { it.id == currentId } ?: Conversation(id = currentId, title = "New Clinical Consult")
        } else null
        val activeAgent = agents.find { it.id == selectedAgentId } ?: agents.find { it.id == "agent_assistant" } ?: agents.firstOrNull()
        Triple(conversations, activeConv, Pair(agents, activeAgent))
    }

    private val modelFlow = combine(
        modelRepository.getActiveModel(),
        modelRepository.getAllModels(),
        _modelStatus
    ) { active, all, status ->
        Triple(active, all, status)
    }

    private val inputFlow = combine(
        _messages,
        _inputText,
        _isStreaming,
        _liveMetrics,
        _lastClassification
    ) { messages, inputText, isStreaming, metrics, classification ->
        InputState(messages, inputText, isStreaming, metrics, classification)
    }

    private val styleDepthFlow = combine(
        _selectedResponseStyle,
        _selectedAnswerDepth
    ) { style, depth ->
        Pair(style, depth)
    }

    private data class InputState(
        val messages: List<Message>,
        val inputText: String,
        val isStreaming: Boolean,
        val metrics: LiveInferenceMetrics,
        val classification: MedicalQueryClassification?
    )

    val uiState: StateFlow<ChatUiState> = combine(
        conversationFlow,
        modelFlow,
        inputFlow,
        styleDepthFlow,
        executionFlow
    ) { (conversations, activeConv, agentPair), (activeModel, allModels, modelStatus), inputState, (selStyle, selDepth), execState ->
        val style = selStyle
            ?: activeConv?.responseStyle?.let { ResponseStyle.fromString(it) }
            ?: preferencesManager?.loadDefaultResponseStyle()
            ?: ResponseStyle.DEFAULT
        val depth = selDepth
            ?: activeConv?.answerDepth?.let { AnswerDepth.fromString(it) }
            ?: preferencesManager?.loadDefaultAnswerDepth()
            ?: AnswerDepth.DEFAULT
        val safeMessages = if (activeConv != null) {
            inputState.messages.filter { it.conversationId == activeConv.id }
        } else {
            emptyList()
        }
        ChatUiState(
            conversations = conversations,
            currentConversation = activeConv,
            messages = safeMessages,
            availableAgents = agentPair.first,
            selectedAgent = agentPair.second,
            inputText = inputState.inputText,
            isStreaming = inputState.isStreaming,
            streamingAgentName = if (inputState.isStreaming) "AgentX Medical" else null,
            activeModel = activeModel,
            allModels = allModels,
            modelStatus = modelStatus,
            activeExecutionState = if (execState.status == ExecutionStatus.RUNNING) execState else null,
            liveMetrics = inputState.metrics,
            currentResponseStyle = style,
            currentAnswerDepth = depth,
            lastQueryClassification = inputState.classification
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = ChatUiState()
    )

    init {
        // On cold start, do not auto-restore last conversation — user starts fresh
        // Prune any legacy empty conversations (0 messages)
        viewModelScope.launch {
            try {
                val all = conversationRepository.getAllConversations().first()
                for (c in all) {
                    val msgs = conversationRepository.getMessagesForConversation(c.id).first()
                    if (msgs.isEmpty()) {
                        conversationRepository.deleteConversation(c.id)
                    }
                }
            } catch (_: Exception) {}
        }

        viewModelScope.launch {
            _currentConversationId.collect { convId ->
                messagesJob?.cancel()
                if (convId != null) {
                    messagesJob = viewModelScope.launch {
                        conversationRepository.getMessagesForConversation(convId).collect { list ->
                            val forThisConv = list.filter { it.conversationId == convId }
                            if (forThisConv.isNotEmpty() || _messages.value.none { it.conversationId == convId }) {
                                _messages.value = forThisConv
                            }
                        }
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

    fun loadActiveModelIntoRam() {
        viewModelScope.launch {
            val activeModel = modelRepository.getActiveModel().first() ?: return@launch
            ensureModelLoaded(activeModel)
            _modelStatus.value = inferenceEngine.getModelStatus()
        }
    }

    fun unloadActiveModelFromRam() {
        viewModelScope.launch {
            try {
                inferenceEngine.unloadModel()
                _modelStatus.value = inferenceEngine.getModelStatus()
            } catch (_: Exception) {}
        }
    }

    fun onInputTextChanged(text: String) {
        _inputText.value = text
    }

    fun onAgentSelected(agent: Agent) {
        _selectedAgentId.value = agent.id
    }

    fun selectConversation(conversationId: String) {
        if (_currentConversationId.value == conversationId) return
        stopGeneration()
        messagesJob?.cancel()
        _messages.value = emptyList()
        _currentConversationId.value = conversationId
        _selectedResponseStyle.value = null
        _selectedAnswerDepth.value = null
        _inputText.value = ""
        _lastClassification.value = null
        _liveMetrics.value = LiveInferenceMetrics()
    }

    fun startNewConversation() {
        stopGeneration()
        messagesJob?.cancel()
        _currentConversationId.value = null
        _messages.value = emptyList()
        _inputText.value = ""
        _lastClassification.value = null
        _liveMetrics.value = LiveInferenceMetrics()
        _selectedResponseStyle.value = preferencesManager?.loadDefaultResponseStyle() ?: ResponseStyle.DEFAULT
        _selectedAnswerDepth.value = preferencesManager?.loadDefaultAnswerDepth() ?: AnswerDepth.DEFAULT
    }

    fun createNewConversation(title: String = "Clinical Consult") {
        if (title == "Clinical Consult" || title == "New Conversation") {
            startNewConversation()
        } else {
            viewModelScope.launch {
                val newId = UUID.randomUUID().toString()
                val initialStyle = _selectedResponseStyle.value
                    ?: preferencesManager?.loadDefaultResponseStyle()
                    ?: ResponseStyle.DEFAULT
                val initialDepth = _selectedAnswerDepth.value
                    ?: preferencesManager?.loadDefaultAnswerDepth()
                    ?: AnswerDepth.DEFAULT
                val newConv = Conversation(
                    id = newId,
                    title = title,
                    activeAgentId = _selectedAgentId.value,
                    responseStyle = initialStyle.name,
                    answerDepth = initialDepth.name
                )
                conversationRepository.createConversation(newConv)
                _currentConversationId.value = newId
            }
        }
    }

    fun updateCurrentConversationStyle(style: ResponseStyle) {
        _selectedResponseStyle.value = style
        preferencesManager?.saveDefaultResponseStyle(style)
        val conv = uiState.value.currentConversation
        if (conv != null) {
            viewModelScope.launch {
                conversationRepository.updateConversation(conv.copy(responseStyle = style.name))
            }
        }
    }

    fun updateCurrentConversationDepth(depth: AnswerDepth) {
        _selectedAnswerDepth.value = depth
        preferencesManager?.saveDefaultAnswerDepth(depth)
        val conv = uiState.value.currentConversation
        if (conv != null) {
            viewModelScope.launch {
                conversationRepository.updateConversation(conv.copy(answerDepth = depth.name))
            }
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
                startNewConversation()
            }
        }
    }

    fun clearCurrentConversation() {
        val convId = _currentConversationId.value ?: return
        viewModelScope.launch {
            stopGeneration()
            conversationRepository.clearMessagesForConversation(convId)
            _messages.value = emptyList()
        }
    }

    fun deleteMessage(messageId: String) {
        viewModelScope.launch {
            conversationRepository.deleteMessage(messageId)
        }
    }

    fun stopGeneration() {
        generationJob?.cancel()
        _isStreaming.value = false
        viewModelScope.launch {
            try {
                inferenceEngine.stopGeneration()
                _modelStatus.value = inferenceEngine.getModelStatus()
            } catch (_: Exception) {}
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
        val currentStatus = inferenceEngine.getModelStatus()
        if ((currentStatus.state == ModelLifecycleState.LOADED ||
             currentStatus.state == ModelLifecycleState.GENERATING ||
             currentStatus.state == ModelLifecycleState.LOADING) &&
            currentStatus.activeModelId == model.id) {
            return
        }
        try {
            inferenceEngine.loadModel(
                ModelConfig(
                    modelId = model.id,
                    modelPath = model.filePath,
                    contextLength = model.contextLength.coerceIn(512, 3840),
                    threads = model.threads.coerceIn(1, 4),
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
        if (text.isEmpty()) return
        if (_isStreaming.value) {
            stopGeneration()
        }

        val state = uiState.value
        val agent = state.selectedAgent
        val agentName = agent?.name ?: "AgentX Medical"

        generationJob?.cancel()
        generationJob = viewModelScope.launch {
            val isNewConv = (_currentConversationId.value == null)
            var convId = _currentConversationId.value
            if (convId == null) {
                convId = UUID.randomUUID().toString()
                val chosenStyle = _selectedResponseStyle.value
                    ?: preferencesManager?.loadDefaultResponseStyle()
                    ?: ResponseStyle.DEFAULT
                val chosenDepth = _selectedAnswerDepth.value
                    ?: preferencesManager?.loadDefaultAnswerDepth()
                    ?: AnswerDepth.DEFAULT
                val convTitle = if (text.length > 35) text.take(32) + "..." else text
                conversationRepository.createConversation(
                    Conversation(
                        id = convId,
                        title = convTitle.replace("\n", " ").trim(),
                        activeAgentId = agent?.id,
                        responseStyle = chosenStyle.name,
                        answerDepth = chosenDepth.name
                    )
                )
                _currentConversationId.value = convId
            }

            // Clean up any stale incomplete streaming messages
            val dirtyMessages = conversationRepository.getMessagesForConversation(convId).first()
                .filter { it.status == MessageStatus.STREAMING }
            for (dirty in dirtyMessages) {
                if (dirty.content.isBlank()) {
                    conversationRepository.deleteMessage(dirty.id)
                } else {
                    conversationRepository.updateMessage(dirty.copy(status = MessageStatus.COMPLETED))
                }
            }

            val userMsg = Message(
                id = UUID.randomUUID().toString(),
                conversationId = convId,
                role = MessageRole.USER,
                senderName = "Clinician",
                content = text,
                status = MessageStatus.COMPLETED
            )
            conversationRepository.sendMessage(userMsg)
            _messages.value = if (isNewConv) {
                listOf(userMsg)
            } else {
                _messages.value.filter {
                    it.conversationId == convId && (it.status != MessageStatus.STREAMING || it.content.isNotBlank())
                } + userMsg
            }
            _inputText.value = ""

            if (agent != null && agent.role == com.agentx.medical.domain.models.AgentRole.SUPERVISOR && clinicalSupervisor != null) {
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
                    agentName = agentName,
                    excludeUserMsgId = userMsg.id
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
        val agentName = agent?.name ?: "AgentX Medical"

        generationJob?.cancel()
        generationJob = viewModelScope.launch {
            // Delete the old assistant response
            conversationRepository.deleteMessage(assistantMessageId)

            if (agent != null && agent.role == com.agentx.medical.domain.models.AgentRole.SUPERVISOR && clinicalSupervisor != null) {
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
            senderName = agentName,
            content = "Clinical analysis in progress...",
            agentId = "agent_supervisor",
            status = MessageStatus.STREAMING
        )
        conversationRepository.sendMessage(assistantMsg)
        _messages.value = _messages.value.filter { it.conversationId == convId } + assistantMsg
        _isStreaming.value = true

        try {
            val coordResult = clinicalSupervisor?.coordinate(
                userGoal = userPrompt,
                conversationId = convId
            )

            if (coordResult != null && coordResult.output.isNotBlank() && coordResult.output != "[No output generated]") {
                assistantMsg = assistantMsg.copy(
                    content = coordResult.output,
                    tokenCount = coordResult.tokenUsage,
                    status = MessageStatus.COMPLETED
                )
                conversationRepository.updateMessage(assistantMsg)
                _isStreaming.value = false
                _modelStatus.value = inferenceEngine.getModelStatus()
            } else {
                streamInferenceIntoMessage(convId, userPrompt, null, assistantMsg)
            }
        } catch (_: Throwable) {
            try {
                streamInferenceIntoMessage(convId, userPrompt, null, assistantMsg)
            } catch (t2: Throwable) {
                assistantMsg = assistantMsg.copy(
                    content = "Unable to complete clinical consult: ${t2.message ?: "Unknown error"}.",
                    status = MessageStatus.ERROR
                )
                conversationRepository.updateMessage(assistantMsg)
                _isStreaming.value = false
                _modelStatus.value = inferenceEngine.getModelStatus()
            }
        }
    }

    private suspend fun executeInference(
        convId: String,
        userPrompt: String,
        agent: Agent?,
        agentName: String,
        excludeUserMsgId: String? = null
    ) {
        val assistantMsgId = UUID.randomUUID().toString()
        val assistantMsg = Message(
            id = assistantMsgId,
            conversationId = convId,
            role = MessageRole.ASSISTANT,
            senderName = agentName,
            content = "",
            agentId = agent?.id,
            status = MessageStatus.STREAMING
        )
        logInfo("ChatViewModel", "executeInference: inserting initial assistantMsg $assistantMsgId for conv $convId")
        conversationRepository.sendMessage(assistantMsg)
        _messages.value = _messages.value.filter { it.conversationId == convId } + assistantMsg
        _isStreaming.value = true

        // Ensure active model is loaded in memory
        try {
            var activeModel = modelRepository.getActiveModel().first()
            if (activeModel == null || !java.io.File(activeModel.filePath).exists()) {
                val candidate = modelRepository.getAllModels().first().firstOrNull { java.io.File(it.filePath).exists() }
                if (candidate != null) {
                    modelRepository.setActiveModel(candidate.id)
                    activeModel = candidate
                } else {
                    val scanDirs = listOf(
                        java.io.File("/data/user/0/com.agentx.medical.debug/files/models"),
                        java.io.File("/data/user/0/com.agentx.medical/files/models"),
                        java.io.File("/sdcard/Download"),
                        java.io.File(android.os.Environment.getExternalStorageDirectory(), "Download"),
                        java.io.File("/sdcard/Android/data/com.agentx.medical.debug/files/models")
                    )
                    for (dir in scanDirs) {
                        val found = dir.listFiles()?.firstOrNull { it.isFile && it.name.endsWith(".gguf", true) }
                        if (found != null && found.exists()) {
                            val autoModel = GGUFModel(
                                id = "storage_" + found.nameWithoutExtension.replace("[^a-zA-Z0-9._-]".toRegex(), "_"),
                                name = found.nameWithoutExtension,
                                filePath = found.absolutePath,
                                uriString = android.net.Uri.fromFile(found).toString(),
                                sizeBytes = found.length(),
                                architecture = "Llama",
                                contextLength = 2048,
                                tensorCount = 0,
                                format = "GGUF v3",
                                quantization = "Q5_K_M",
                                capabilityProfile = "clinical-consultant",
                                isStaged = true,
                                isActive = true
                            )
                            modelRepository.addModel(autoModel)
                            modelRepository.setActiveModel(autoModel.id)
                            activeModel = autoModel
                            break
                        }
                    }
                }
            }
            if (activeModel != null && activeModel.filePath.isNotEmpty() && java.io.File(activeModel.filePath).exists()) {
                ensureModelLoaded(activeModel)
            }
        } catch (_: Throwable) { }

        streamInferenceIntoMessage(convId, userPrompt, agent, assistantMsg, excludeUserMsgId)
    }

    private suspend fun streamInferenceIntoMessage(
        convId: String,
        userPrompt: String,
        agent: Agent?,
        initialMsg: Message,
        excludeUserMsgId: String? = null
    ) {
        var assistantMsg = initialMsg

        // Context Budgeting & Sliding Window Construction: only feed completed non-empty messages
        val historyMessages = conversationRepository.getMessagesForConversation(convId).first()
            .filter { it.id != assistantMsg.id && (excludeUserMsgId == null || it.id != excludeUserMsgId) && it.status == MessageStatus.COMPLETED && it.content.isNotBlank() }

        // 1. Dynamic Medical Query Classification
        val classification = MedicalQueryClassifier.classify(userPrompt)
        _lastClassification.value = classification

        // 2. Resolve Active Conversation Style & Depth
        val activeConv = conversationRepository.getConversationById(convId)
        val style = _selectedResponseStyle.value
            ?: activeConv?.responseStyle?.let { ResponseStyle.fromString(it) }
            ?: preferencesManager?.loadDefaultResponseStyle()
            ?: ResponseStyle.DEFAULT
        val depth = _selectedAnswerDepth.value
            ?: activeConv?.answerDepth?.let { AnswerDepth.fromString(it) }
            ?: preferencesManager?.loadDefaultAnswerDepth()
            ?: AnswerDepth.DEFAULT

        // 3. Build Specialized Clinical System Prompt
        val synthesizedSystemPrompt = MedicalPromptBuilder.buildSystemPrompt(
            classification = classification,
            style = style,
            depth = depth
        )

        // Augment user prompt with style and depth anchors for local model steering
        val augmentedUserPrompt = MedicalPromptBuilder.augmentUserPrompt(
            userPrompt = userPrompt,
            style = style,
            depth = depth
        )

        val activeModelConfig = modelRepository.getActiveModel().first()
        val extendedConfig = ExtendedModelConfig.parse(activeModelConfig?.capabilityProfile)
        val maxContext = (activeModelConfig?.contextLength ?: 3840).coerceIn(512, 8192)

        val estimatedSystemTokens = ContextManager.estimateTokens(synthesizedSystemPrompt)
        val estimatedUserTokens = ContextManager.estimateTokens(augmentedUserPrompt)
        val maxOutputTokens = MedicalPromptBuilder.computeMaxTokens(
            depth = depth,
            contextLength = maxContext,
            estimatedPromptTokens = estimatedSystemTokens + estimatedUserTokens
        )

        val contextualPrompt = ContextManager.buildPromptWithContext(
            systemPrompt = null, // Formatted in native chat template
            history = historyMessages,
            currentPrompt = augmentedUserPrompt,
            budget = ContextBudget(
                maxContextTokens = maxContext,
                maxOutputTokens = maxOutputTokens,
                reservedTokens = 64
            )
        )

        val baseTemp = activeModelConfig?.temperature ?: agent?.temperature ?: 0.7f
        val tunedTemp = when (style) {
            ResponseStyle.RESEARCH -> (baseTemp * 0.7f).coerceIn(0.2f, 1.0f)
            ResponseStyle.QUICK_REFERENCE_VIVA -> (baseTemp * 0.8f).coerceIn(0.3f, 1.0f)
            ResponseStyle.CLINICAL_PROFESSIONAL -> (baseTemp * 0.85f).coerceIn(0.3f, 1.0f)
            ResponseStyle.MEDICAL_STUDENT -> baseTemp
            ResponseStyle.ADVANCED -> baseTemp
        }

        val buffer = StringBuilder()
        val startTime = System.currentTimeMillis()
        var tokenCount = 0
        var lastDbUpdateTime = System.currentTimeMillis()
        var lastUiUpdateTime = System.currentTimeMillis()

        _liveMetrics.value = LiveInferenceMetrics(
            isLive = true,
            tokensGenerated = 0,
            tokensPerSecond = 0f,
            elapsedSeconds = 0f,
            memoryUsageMb = inferenceEngine.getModelStatus().memoryUsageBytes / (1024 * 1024)
        )

        try {
            logInfo("ChatViewModel", "Starting inferenceEngine.generate for msg ${assistantMsg.id} with style=$style, depth=$depth, temp=$tunedTemp")
            val stopSeqs = (extendedConfig.stopSequences + listOf("<|im_end|>", "<|endoftext|>", "\nUser:", "\nClinician:")).distinct()
            inferenceEngine.generate(
                InferenceRequest(
                    prompt = contextualPrompt,
                    systemPrompt = synthesizedSystemPrompt,
                    maxTokens = maxOutputTokens,
                    temperature = tunedTemp,
                    topP = activeModelConfig?.topP ?: agent?.topP ?: 0.9f,
                    topK = extendedConfig.topK,
                    repeatPenalty = extendedConfig.repetitionPenalty,
                    seed = extendedConfig.seed,
                    stopSequences = stopSeqs
                )
            ).collect { event ->
                when (event) {
                    is GenerationEvent.Token -> {
                        buffer.append(event.text)
                        tokenCount++

                        val now = System.currentTimeMillis()
                        if (now - lastUiUpdateTime >= 40L || tokenCount <= 3) {
                            lastUiUpdateTime = now
                            val elapsedMs = (now - startTime).coerceAtLeast(1)
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
                            if (_currentConversationId.value == convId) {
                                val current = _messages.value
                                if (current.none { it.id == assistantMsg.id }) {
                                    _messages.value = current + assistantMsg
                                } else {
                                    _messages.value = current.map {
                                        if (it.id == assistantMsg.id) assistantMsg else it
                                    }
                                }
                            }
                        }

                        if (now - lastDbUpdateTime >= 400L || tokenCount % 20 == 0) {
                            lastDbUpdateTime = now
                            conversationRepository.updateMessage(assistantMsg)
                        }
                    }
                    is GenerationEvent.Finished -> {
                        logInfo("ChatViewModel", "Finished event! totalTokens=${event.totalTokens}, buffer length=${buffer.length}")
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
                        if (_currentConversationId.value == convId) {
                            val current = _messages.value
                            if (current.none { it.id == assistantMsg.id }) {
                                _messages.value = current + assistantMsg
                            } else {
                                _messages.value = current.map {
                                    if (it.id == assistantMsg.id) assistantMsg else it
                                }
                            }
                        }
                        conversationRepository.updateMessage(assistantMsg)
                        _isStreaming.value = false
                        _modelStatus.value = inferenceEngine.getModelStatus()
                    }
                    is GenerationEvent.Error -> {
                        logError("ChatViewModel", "Error event received: ${event.message}")
                        _liveMetrics.value = _liveMetrics.value.copy(isLive = false)
                        val currentText = buffer.toString()
                        val errorDetail = event.message.orEmpty()
                        val diagnostic = when {
                            errorDetail.contains("unauthorized", ignoreCase = true) || errorDetail.contains("401") ->
                                "Authentication failed (HTTP 401). Please check the API Key in Settings -> Cloud Models."
                            errorDetail.contains("connect", ignoreCase = true) || errorDetail.contains("refused", ignoreCase = true) ->
                                "Could not connect to model host ($errorDetail). Verify that AgentX Serve is active on your Wi-Fi network and check the IP in Settings."
                            errorDetail.contains("No model loaded", ignoreCase = true) || errorDetail.contains("No GGUF model", ignoreCase = true) ->
                                "No local GGUF model is loaded. Please import a .gguf model in the Models tab or select an active model."
                            else -> errorDetail.ifBlank { "Clinical inference generation encountered an error" }
                        }
                        assistantMsg = assistantMsg.copy(
                            content = if (currentText.isNotEmpty()) currentText else "⚠️ Connection / Clinical Inference Notice:\n$diagnostic\n\nTip: You can change model providers or configure endpoints in Settings.",
                            status = MessageStatus.ERROR
                        )
                        if (_currentConversationId.value == convId) {
                            val current = _messages.value
                            if (current.none { it.id == assistantMsg.id }) {
                                _messages.value = current + assistantMsg
                            } else {
                                _messages.value = current.map {
                                    if (it.id == assistantMsg.id) assistantMsg else it
                                }
                            }
                        }
                        conversationRepository.updateMessage(assistantMsg)
                        _isStreaming.value = false
                        _modelStatus.value = inferenceEngine.getModelStatus()
                    }
                }
            }
            logInfo("ChatViewModel", "Inference collect finished normally for msg ${assistantMsg.id}")
        } catch (e: Throwable) {
            logError("ChatViewModel", "Inference collect exception for msg ${assistantMsg.id}", e)
            throw e
        } finally {
            logInfo("ChatViewModel", "finally block: status=${assistantMsg.status}, bufferLen=${buffer.length}")
            _isStreaming.value = false
            _liveMetrics.value = _liveMetrics.value.copy(isLive = false)
            _modelStatus.value = inferenceEngine.getModelStatus()
            // If message remained in STREAMING state due to cancellation, finalize it
            if (assistantMsg.status == MessageStatus.STREAMING) {
                val finalContent = buffer.toString()
                if (finalContent.isNotBlank()) {
                    val finalized = assistantMsg.copy(
                        content = finalContent,
                        tokenCount = tokenCount,
                        status = MessageStatus.COMPLETED
                    )
                    _messages.value = _messages.value.map {
                        if (it.id == assistantMsg.id) finalized else it
                    }
                    conversationRepository.updateMessage(finalized)
                } else {
                    logWarn("ChatViewModel", "Deleting streaming message ${assistantMsg.id} because buffer is blank!")
                    _messages.value = _messages.value.filter { it.id != assistantMsg.id }
                    conversationRepository.deleteMessage(assistantMsg.id)
                }
            }
        }
    }

    private fun logInfo(tag: String, message: String) {
        try {
            android.util.Log.i(tag, message)
        } catch (_: Throwable) {
            println("[$tag] $message")
        }
    }

    private fun logWarn(tag: String, message: String) {
        try {
            android.util.Log.w(tag, message)
        } catch (_: Throwable) {
            println("[$tag] WARN: $message")
        }
    }

    private fun logError(tag: String, message: String, throwable: Throwable? = null) {
        try {
            if (throwable != null) {
                android.util.Log.e(tag, message, throwable)
            } else {
                android.util.Log.e(tag, message)
            }
        } catch (_: Throwable) {
            System.err.println("[$tag] ERROR: $message ${throwable?.message.orEmpty()}")
        }
    }
}
