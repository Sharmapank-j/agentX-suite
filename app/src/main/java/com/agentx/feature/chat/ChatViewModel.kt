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
import com.agentx.core.inference.CloudModelConfig
import com.agentx.core.inference.CloudProvider
import com.agentx.core.inference.HybridInferenceEngine
import com.agentx.core.inference.InferenceMode
import com.agentx.core.multimodal.ImageUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import com.agentx.core.inference.model.ExtendedModelConfig
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
    val attachedImagePath: String? = null,
    val attachedImageMimeType: String? = null,
    val inferenceMode: InferenceMode = InferenceMode.LOCAL_ONLY,
    val cloudConfig: CloudModelConfig = CloudModelConfig(),
    val isMultimodalCapable: Boolean = false
)

class ChatViewModel(
    private val conversationRepository: ConversationRepository,
    private val agentRepository: AgentRepository,
    private val modelRepository: ModelRepository,
    private val inferenceEngine: InferenceEngine,
    private val clinicalSupervisor: ClinicalSupervisor? = null,
    private val executionQueue: ExecutionQueue? = null,
    private val memoryManager: com.agentx.core.memory.DefaultLongTermMemoryManager? = null
) : ViewModel() {

    private val _currentConversationId = MutableStateFlow<String?>(null)
    private val _selectedAgentId = MutableStateFlow<String?>("agent_assistant")
    private val _inputText = MutableStateFlow("")
    private val _isStreaming = MutableStateFlow(false)
    private val _messages = MutableStateFlow<List<Message>>(emptyList())
    private val _modelStatus = MutableStateFlow(inferenceEngine.getModelStatus())
    private val _liveMetrics = MutableStateFlow(LiveInferenceMetrics())
    private val _attachedImagePath = MutableStateFlow<String?>(null)
    private val _attachedImageMimeType = MutableStateFlow<String?>(null)
    private val _inferenceMode = MutableStateFlow((inferenceEngine as? HybridInferenceEngine)?.inferenceMode ?: InferenceMode.LOCAL_ONLY)
    private val _cloudConfig = MutableStateFlow((inferenceEngine as? HybridInferenceEngine)?.cloudEngine?.config ?: CloudModelConfig())
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
        _attachedImagePath,
        _attachedImageMimeType
    ) { args ->
        @Suppress("UNCHECKED_CAST")
        InputState(
            messages = args[0] as List<Message>,
            inputText = args[1] as String,
            isStreaming = args[2] as Boolean,
            metrics = args[3] as LiveInferenceMetrics,
            attachedImagePath = args[4] as? String,
            attachedImageMimeType = args[5] as? String
        )
    }

    private data class InputState(
        val messages: List<Message>,
        val inputText: String,
        val isStreaming: Boolean,
        val metrics: LiveInferenceMetrics,
        val attachedImagePath: String?,
        val attachedImageMimeType: String?
    )

    val uiState: StateFlow<ChatUiState> = combine(
        conversationFlow,
        modelFlow,
        inputFlow,
        executionFlow,
        _inferenceMode
    ) { (conversations, activeConv, agentPair), (activeModel, allModels, modelStatus), inputState, execState, infMode ->
        val currentCloudConfig = _cloudConfig.value
        val isMultimodal = isMultimodalCapable(infMode, currentCloudConfig, activeModel)
        if (!isMultimodal && inputState.attachedImagePath != null) {
            _attachedImagePath.value = null
            _attachedImageMimeType.value = null
        }
        ChatUiState(
            conversations = conversations,
            currentConversation = activeConv,
            messages = inputState.messages,
            availableAgents = agentPair.first,
            selectedAgent = agentPair.second,
            inputText = inputState.inputText,
            isStreaming = inputState.isStreaming,
            streamingAgentName = if (inputState.isStreaming) "AgentX" else null,
            activeModel = activeModel,
            allModels = allModels,
            modelStatus = modelStatus,
            activeExecutionState = if (execState.status == ExecutionStatus.RUNNING) execState else null,
            liveMetrics = inputState.metrics,
            attachedImagePath = if (isMultimodal) inputState.attachedImagePath else null,
            attachedImageMimeType = if (isMultimodal) inputState.attachedImageMimeType else null,
            inferenceMode = infMode,
            cloudConfig = currentCloudConfig,
            isMultimodalCapable = isMultimodal
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = ChatUiState()
    )

    private fun isMultimodalCapable(
        inferenceMode: InferenceMode,
        cloudConfig: CloudModelConfig,
        activeModel: GGUFModel?
    ): Boolean {
        return when (inferenceMode) {
            InferenceMode.CLOUD_ONLY -> {
                if (cloudConfig.provider == CloudProvider.GEMINI) true
                else {
                    val m = cloudConfig.modelName.lowercase()
                    m.contains("vision") || m.contains("vl") || m.contains("llava") || m.contains("gemini") || m.contains("moondream") || m.contains("gpt-4")
                }
            }
            InferenceMode.HYBRID_AUTO -> {
                if (cloudConfig.provider == CloudProvider.GEMINI) true
                else {
                    val m = cloudConfig.modelName.lowercase()
                    val cloudVision = m.contains("vision") || m.contains("vl") || m.contains("llava") || m.contains("gemini") || m.contains("moondream")
                    cloudVision || isModelMultimodal(activeModel)
                }
            }
            InferenceMode.LOCAL_ONLY -> {
                isModelMultimodal(activeModel)
            }
        }
    }

    private fun isModelMultimodal(model: GGUFModel?): Boolean {
        if (model == null) return false
        val lowerName = model.name.lowercase()
        val lowerArch = model.architecture.lowercase()
        val lowerProfile = model.capabilityProfile.lowercase()
        return lowerName.contains("vision") || lowerName.contains("-vl") || lowerName.contains("llava") ||
               lowerName.contains("moondream") || lowerName.contains("minicpm-v") ||
               lowerArch.contains("clip") || lowerArch.contains("vision") ||
               lowerProfile.contains("multimodal") || lowerProfile.contains("vision")
    }

    init {
        viewModelScope.launch {
            conversationRepository.getAllConversations().collect { convs ->
                if (_currentConversationId.value == null && convs.isNotEmpty()) {
                    _currentConversationId.value = convs.first().id
                }
            }
        }

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

    fun attachImage(path: String, mimeType: String = "image/jpeg") {
        if (!uiState.value.isMultimodalCapable) return
        _attachedImagePath.value = path
        _attachedImageMimeType.value = mimeType
    }

    fun removeAttachment() {
        _attachedImagePath.value = null
        _attachedImageMimeType.value = null
    }

    fun setInferenceMode(mode: InferenceMode) {
        _inferenceMode.value = mode
        (inferenceEngine as? HybridInferenceEngine)?.inferenceMode = mode
    }

    fun updateCloudConfig(config: CloudModelConfig) {
        _cloudConfig.value = config
        (inferenceEngine as? HybridInferenceEngine)?.cloudEngine?.config = config
    }

    fun sendMessage() {
        val text = _inputText.value.trim()
        val attachmentPath = _attachedImagePath.value
        val attachmentMime = _attachedImageMimeType.value

        if (text.isEmpty() && attachmentPath == null) return
        if (_isStreaming.value) {
            stopGeneration()
        }

        _attachedImagePath.value = null
        _attachedImageMimeType.value = null

        val promptText = if (text.isBlank() && !attachmentPath.isNullOrBlank()) {
            "Analyze and describe this image in detail."
        } else {
            text
        }

        val state = uiState.value
        val agent = state.selectedAgent
        val agentName = agent?.name ?: "AgentX"

        generationJob?.cancel()
        generationJob = viewModelScope.launch {
            var convId = _currentConversationId.value ?: state.currentConversation?.id
            if (convId == null) {
                convId = UUID.randomUUID().toString()
                conversationRepository.createConversation(
                    Conversation(
                        id = convId,
                        title = promptText.take(30),
                        activeAgentId = agent?.id
                    )
                )
            }
            _currentConversationId.value = convId

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
                senderName = "User",
                content = promptText,
                status = MessageStatus.COMPLETED,
                attachmentUri = attachmentPath,
                attachmentMimeType = attachmentMime
            )
            conversationRepository.sendMessage(userMsg)
            _messages.value = _messages.value.filter { it.status != MessageStatus.STREAMING || it.content.isNotBlank() } + userMsg
            _inputText.value = ""

            if (agent != null && agent.role == AgentRole.SUPERVISOR && clinicalSupervisor != null) {
                executeMultiAgentSupervisor(
                    convId = convId,
                    userPrompt = promptText,
                    agentName = agentName
                )
            } else {
                executeInference(
                    convId = convId,
                    userPrompt = promptText,
                    agent = agent,
                    agentName = agentName,
                    attachmentPath = attachmentPath,
                    attachmentMime = attachmentMime
                )
            }
        }
    }

    fun regenerateResponse(assistantMessageId: String) {
        if (_isStreaming.value) return
        val currentMessages = _messages.value
        val targetIndex = currentMessages.indexOfFirst { it.id == assistantMessageId }
        if (targetIndex == -1) return

        // Find the preceding user query and attachment
        val userMsg = currentMessages.take(targetIndex).lastOrNull { it.role == MessageRole.USER }
        val userQuery = userMsg?.content ?: return
        val attachmentPath = userMsg.attachmentUri
        val attachmentMime = userMsg.attachmentMimeType

        val convId = _currentConversationId.value ?: return
        val agent = uiState.value.selectedAgent
        val agentName = agent?.name ?: "AgentX"

        generationJob?.cancel()
        generationJob = viewModelScope.launch {
            // Delete the old assistant response
            conversationRepository.deleteMessage(assistantMessageId)

            if (agent != null && agent.role == AgentRole.SUPERVISOR && clinicalSupervisor != null) {
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
                    agentName = agentName,
                    attachmentPath = attachmentPath,
                    attachmentMime = attachmentMime
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
            content = "Thinking...",
            agentId = "agent_supervisor",
            status = MessageStatus.STREAMING
        )
        conversationRepository.sendMessage(assistantMsg)
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
                    content = "Unable to process request: ${t2.message ?: "Unknown error"}.",
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
        attachmentPath: String? = null,
        attachmentMime: String? = null
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
        conversationRepository.sendMessage(assistantMsg)
        _messages.value = _messages.value + assistantMsg
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
                        java.io.File("/data/user/0/com.agentx/files/models"),
                        java.io.File("/sdcard/Download"),
                        java.io.File(android.os.Environment.getExternalStorageDirectory(), "Download"),
                        java.io.File("/sdcard/Android/data/com.agentx/files/models")
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
                                capabilityProfile = "general-assistant",
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

        streamInferenceIntoMessage(convId, userPrompt, agent, assistantMsg, attachmentPath, attachmentMime)
    }

    private suspend fun streamInferenceIntoMessage(
        convId: String,
        userPrompt: String,
        agent: Agent?,
        initialMsg: Message,
        attachmentPath: String? = null,
        attachmentMime: String? = null
    ) {
        var assistantMsg = initialMsg

        // Context Attention & Memory Management: Multi-layer attention allocation
        val historyMessages = conversationRepository.getMessagesForConversation(convId).first()
            .filter { it.id != assistantMsg.id && it.status == MessageStatus.COMPLETED && it.content.isNotBlank() }

        val activeModelConfig = modelRepository.getActiveModel().first()
        val extendedConfig = ExtendedModelConfig.parse(activeModelConfig?.capabilityProfile)
        val maxContext = (activeModelConfig?.contextLength ?: 2048).coerceIn(512, 3840)
        val effectiveSystemPrompt = extendedConfig.systemPrompt ?: agent?.systemPrompt
        val maxOutputTokens = extendedConfig.maxTokens.takeIf { it > 0 } ?: (agent?.maxTokens ?: 512)

        val contextMessages = historyMessages.map { msg ->
            com.agentx.core.context.ContextMessage(
                id = msg.id,
                role = if (msg.role == MessageRole.USER) "user" else "assistant",
                senderName = msg.senderName,
                content = msg.content
            )
        }

        val relevantMemories = try {
            memoryManager?.search(com.agentx.core.memory.MemoryQuery(query = userPrompt, limit = 3)) ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }

        val optimizedContext = com.agentx.core.context.ContextAttentionManager.buildOptimizedPrompt(
            systemPrompt = effectiveSystemPrompt,
            userProfileMemories = relevantMemories,
            history = contextMessages,
            currentPrompt = userPrompt,
            budget = com.agentx.core.context.AttentionBudget(
                maxContextTokens = maxContext,
                maxOutputTokens = maxOutputTokens,
                safetyMarginTokens = 48
            )
        )
        val contextualPrompt = optimizedContext.fullPrompt

        // Multimodal Image Base64 Encoding
        var imageBase64: String? = null
        if (!attachmentPath.isNullOrBlank()) {
            try {
                val bitmap = ImageUtils.decodeSampledBitmap(attachmentPath, 1024, 1024)
                if (bitmap != null) {
                    imageBase64 = ImageUtils.encodeToBase64(bitmap, quality = 80)
                }
            } catch (_: Throwable) { }
        }

        val buffer = StringBuilder()
        val startTime = System.currentTimeMillis()
        var tokenCount = 0
        var lastDbUpdateTime = System.currentTimeMillis()

        _liveMetrics.value = LiveInferenceMetrics(
            isLive = true,
            tokensGenerated = 0,
            tokensPerSecond = 0f,
            elapsedSeconds = 0f,
            memoryUsageMb = inferenceEngine.getModelStatus().memoryUsageBytes / (1024 * 1024)
        )

        try {
            inferenceEngine.generate(
                InferenceRequest(
                    prompt = contextualPrompt,
                    systemPrompt = effectiveSystemPrompt,
                    maxTokens = maxOutputTokens,
                    temperature = activeModelConfig?.temperature ?: agent?.temperature ?: 0.7f,
                    topP = activeModelConfig?.topP ?: agent?.topP ?: 0.9f,
                    topK = extendedConfig.topK,
                    repeatPenalty = extendedConfig.repetitionPenalty,
                    seed = extendedConfig.seed,
                    stopSequences = extendedConfig.stopSequences,
                    imageBase64 = imageBase64,
                    imageMimeType = attachmentMime ?: "image/jpeg"
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
                        _messages.value = _messages.value.map {
                            if (it.id == assistantMsg.id) assistantMsg else it
                        }
                        val now = System.currentTimeMillis()
                        if (now - lastDbUpdateTime >= 250L || tokenCount % 15 == 0) {
                            lastDbUpdateTime = now
                            conversationRepository.updateMessage(assistantMsg)
                        }
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
                        _messages.value = _messages.value.map {
                            if (it.id == assistantMsg.id) assistantMsg else it
                        }
                        conversationRepository.updateMessage(assistantMsg)
                        _isStreaming.value = false
                        _modelStatus.value = inferenceEngine.getModelStatus()

                        // Asynchronously extract and store learned facts in long-term memory
                        viewModelScope.launch(Dispatchers.IO) {
                            try {
                                val extractedFacts = memoryManager?.extractFactsFromUserStatement(userPrompt) ?: emptyList()
                                for (fact in extractedFacts) {
                                    memoryManager?.store(fact)
                                }
                            } catch (_: Exception) {}
                        }
                    }
                    is GenerationEvent.Error -> {
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
                            else -> errorDetail.ifBlank { "Inference generation encountered an error" }
                        }
                        assistantMsg = assistantMsg.copy(
                            content = if (currentText.isNotEmpty()) currentText else "⚠️ Connection / Inference Notice:\n$diagnostic\n\nTip: You can change model providers or configure endpoints in Settings.",
                            status = MessageStatus.ERROR
                        )
                        _messages.value = _messages.value.map {
                            if (it.id == assistantMsg.id) assistantMsg else it
                        }
                        conversationRepository.updateMessage(assistantMsg)
                        _isStreaming.value = false
                        _modelStatus.value = inferenceEngine.getModelStatus()

                        // Asynchronously extract and store learned facts in long-term memory
                        viewModelScope.launch(Dispatchers.IO) {
                            try {
                                val extractedFacts = memoryManager?.extractFactsFromUserStatement(userPrompt) ?: emptyList()
                                for (fact in extractedFacts) {
                                    memoryManager?.store(fact)
                                }
                            } catch (_: Exception) {}
                        }
                    }
                }
            }
        } finally {
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
                    _messages.value = _messages.value.filter { it.id != assistantMsg.id }
                    conversationRepository.deleteMessage(assistantMsg.id)
                }
            }
        }
    }
}
