package com.agentx

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
import com.agentx.domain.models.GGUFModel
import com.agentx.domain.models.Message
import com.agentx.domain.models.MessageRole
import com.agentx.domain.models.MessageStatus
import com.agentx.feature.chat.ChatViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var conversationRepo: FakeConversationRepository
    private lateinit var agentRepo: FakeAgentRepository
    private lateinit var modelRepo: FakeModelRepository
    private lateinit var inferenceEngine: FakeInferenceEngine
    private lateinit var viewModel: ChatViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        conversationRepo = FakeConversationRepository()
        agentRepo = FakeAgentRepository()
        modelRepo = FakeModelRepository()
        inferenceEngine = FakeInferenceEngine()

        viewModel = ChatViewModel(
            conversationRepository = conversationRepo,
            agentRepository = agentRepo,
            modelRepository = modelRepo,
            inferenceEngine = inferenceEngine
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun testInitialStateLoadsDefaultAgentsAndConversations() = runTest(testDispatcher) {
        backgroundScope.launch { viewModel.uiState.collect { } }
        testScheduler.advanceUntilIdle()
        val state = viewModel.uiState.value
        assertEquals("Chief Clinical Supervisor", state.availableAgents.firstOrNull()?.name)
        assertEquals("Chief Clinical Supervisor", state.selectedAgent?.name)
    }

    @Test
    fun testCreateNewConversation() = runTest(testDispatcher) {
        backgroundScope.launch { viewModel.uiState.collect { } }
        testScheduler.advanceUntilIdle()
        viewModel.createNewConversation("Cardiology Consult")
        testScheduler.advanceUntilIdle()

        val conversations = conversationRepo.conversations.value
        assertTrue(conversations.any { it.title == "Cardiology Consult" })
    }

    @Test
    fun testRenameConversation() = runTest(testDispatcher) {
        backgroundScope.launch { viewModel.uiState.collect { } }
        testScheduler.advanceUntilIdle()
        viewModel.createNewConversation("Old Name")
        testScheduler.advanceUntilIdle()
        val created = conversationRepo.conversations.value.first { it.title == "Old Name" }

        viewModel.renameConversation(created.id, "Renamed Cardiology Consult")
        testScheduler.advanceUntilIdle()

        val updated = conversationRepo.getConversationById(created.id)
        assertEquals("Renamed Cardiology Consult", updated?.title)
    }

    @Test
    fun testDeleteConversation() = runTest(testDispatcher) {
        backgroundScope.launch { viewModel.uiState.collect { } }
        testScheduler.advanceUntilIdle()
        viewModel.createNewConversation("To Delete")
        testScheduler.advanceUntilIdle()
        val toDelete = conversationRepo.conversations.value.first { it.title == "To Delete" }

        viewModel.deleteConversation(toDelete.id)
        testScheduler.advanceUntilIdle()

        assertFalse(conversationRepo.conversations.value.any { it.id == toDelete.id })
    }

    @Test
    fun testSendMessageTriggersInferenceAndSavesMessages() = runTest(testDispatcher) {
        backgroundScope.launch { viewModel.uiState.collect { } }
        testScheduler.advanceUntilIdle()
        viewModel.onInputTextChanged("What is the first-line treatment for pulmonary embolism?")
        viewModel.sendMessage()
        testScheduler.advanceUntilIdle()

        val messages = conversationRepo.allMessages.value
        assertTrue("User message should be stored", messages.any { it.role == MessageRole.USER })
        assertTrue("Assistant message should be stored", messages.any { it.role == MessageRole.ASSISTANT })

        val assistantMsg = messages.first { it.role == MessageRole.ASSISTANT }
        assertEquals(MessageStatus.COMPLETED, assistantMsg.status)
        assertTrue(assistantMsg.content.contains("Inference complete"))
    }

    @Test
    fun testDeleteMessageRemovesFromRepository() = runTest(testDispatcher) {
        backgroundScope.launch { viewModel.uiState.collect { } }
        testScheduler.advanceUntilIdle()
        viewModel.onInputTextChanged("Temporary note")
        viewModel.sendMessage()
        testScheduler.advanceUntilIdle()

        val msgToDelete = conversationRepo.allMessages.value.first()
        viewModel.deleteMessage(msgToDelete.id)
        testScheduler.advanceUntilIdle()

        assertFalse(conversationRepo.allMessages.value.any { it.id == msgToDelete.id })
    }

    @Test
    fun testModelSwitchingUpdatesState() = runTest(testDispatcher) {
        backgroundScope.launch { viewModel.uiState.collect { } }
        testScheduler.advanceUntilIdle()
        val newModel = GGUFModel(
            id = "model_meditron_7b",
            name = "Meditron 7B",
            filePath = "/path/to/meditron.gguf",
            uriString = "content://models/meditron.gguf",
            sizeBytes = 4L * 1024 * 1024 * 1024,
            contextLength = 4096
        )
        modelRepo.models.value = listOf(newModel)

        viewModel.switchActiveModel("model_meditron_7b")
        testScheduler.advanceUntilIdle()

        assertEquals("model_meditron_7b", modelRepo.activeModel.value?.id)
        assertEquals(ModelLifecycleState.LOADED, inferenceEngine.status.state)
    }

    @Test
    fun testStopGenerationCancelsEngine() = runTest(testDispatcher) {
        backgroundScope.launch { viewModel.uiState.collect { } }
        testScheduler.advanceUntilIdle()
        viewModel.onInputTextChanged("Long running prompt")
        viewModel.sendMessage()
        viewModel.stopGeneration()
        testScheduler.advanceUntilIdle()

        assertTrue(inferenceEngine.stopped)
        assertFalse(viewModel.uiState.value.isStreaming)
    }

    @Test
    fun testTextOnlyModelDisablesMultimodalCapabilities() = runTest(testDispatcher) {
        backgroundScope.launch { viewModel.uiState.collect { } }
        val textModel = GGUFModel(
            id = "smollm_360m",
            name = "SmolLM-360M-Instruct",
            filePath = "/data/models/smollm.gguf",
            uriString = "content://models/smollm.gguf",
            sizeBytes = 380_000_000L,
            architecture = "llama",
            capabilityProfile = "general-assistant"
        )
        modelRepo.activeModel.value = textModel
        testScheduler.advanceUntilIdle()

        assertFalse("Text-only model should not be multimodal capable", viewModel.uiState.value.isMultimodalCapable)

        // Attempting to attach image on text-only model should be ignored
        viewModel.attachImage("/path/to/image.jpg", "image/jpeg")
        testScheduler.advanceUntilIdle()
        assertEquals(null, viewModel.uiState.value.attachedImagePath)
    }

    @Test
    fun testVisionCapableModelEnablesMultimodalCapabilities() = runTest(testDispatcher) {
        backgroundScope.launch { viewModel.uiState.collect { } }
        val visionModel = GGUFModel(
            id = "llava_v1.6_7b",
            name = "LLaVA-v1.6-7B-Vision",
            filePath = "/data/models/llava.gguf",
            uriString = "content://models/llava.gguf",
            sizeBytes = 4_000_000_000L,
            architecture = "llava",
            capabilityProfile = "multimodal-vision"
        )
        modelRepo.activeModel.value = visionModel
        testScheduler.advanceUntilIdle()

        assertTrue("Vision model should be multimodal capable", viewModel.uiState.value.isMultimodalCapable)

        viewModel.attachImage("/path/to/diagram.png", "image/png")
        testScheduler.advanceUntilIdle()
        assertEquals("/path/to/diagram.png", viewModel.uiState.value.attachedImagePath)
    }

    @Test
    fun testStartNewConversationSeparatesMessagesFromPreviousConversation() = runTest(testDispatcher) {
        backgroundScope.launch { viewModel.uiState.collect { } }
        testScheduler.advanceUntilIdle()

        // Conversation 1
        viewModel.onInputTextChanged("Query for Session 1")
        viewModel.sendMessage()
        testScheduler.advanceUntilIdle()

        val conv1Id = viewModel.uiState.value.currentConversation?.id
        assertNotNull("Session 1 should have a valid conversation ID", conv1Id)
        val conv1Messages = viewModel.uiState.value.messages
        assertEquals(2, conv1Messages.size)
        assertTrue(conv1Messages.any { it.content == "Query for Session 1" })

        // Start New Conversation
        viewModel.startNewConversation()
        testScheduler.advanceUntilIdle()

        assertEquals(null, viewModel.uiState.value.currentConversation)
        assertEquals(0, viewModel.uiState.value.messages.size)

        // Conversation 2
        viewModel.onInputTextChanged("Query for Session 2")
        viewModel.sendMessage()
        testScheduler.advanceUntilIdle()

        val conv2Id = viewModel.uiState.value.currentConversation?.id
        assertNotNull("Session 2 should have a valid conversation ID", conv2Id)
        assertTrue("Session 2 ID must be distinct from Session 1", conv1Id != conv2Id)

        val conv2Messages = viewModel.uiState.value.messages
        assertEquals(2, conv2Messages.size)
        assertTrue("Session 2 messages must contain Session 2 query", conv2Messages.any { it.content == "Query for Session 2" })
        assertTrue("Session 2 messages must NOT contain Session 1 content", conv2Messages.none { it.content.contains("Session 1") })
    }

    @Test
    fun testSwitchingConversationsLoadsOnlySelectedConversationMessages() = runTest(testDispatcher) {
        backgroundScope.launch { viewModel.uiState.collect { } }
        testScheduler.advanceUntilIdle()

        // Create Chat A
        viewModel.onInputTextChanged("Chat A Prompt")
        viewModel.sendMessage()
        testScheduler.advanceUntilIdle()
        val chatAId = viewModel.uiState.value.currentConversation!!.id

        // Start New Conversation and Create Chat B
        viewModel.startNewConversation()
        testScheduler.advanceUntilIdle()
        viewModel.onInputTextChanged("Chat B Prompt")
        viewModel.sendMessage()
        testScheduler.advanceUntilIdle()
        val chatBId = viewModel.uiState.value.currentConversation!!.id

        // Switch back to Chat A
        viewModel.selectConversation(chatAId)
        testScheduler.advanceUntilIdle()

        val loadedA = viewModel.uiState.value.messages
        assertTrue("Loaded Chat A must have messages", loadedA.isNotEmpty())
        assertTrue("Loaded Chat A must only contain Chat A messages", loadedA.all { it.conversationId == chatAId })
        assertTrue("Loaded Chat A must not contain Chat B messages", loadedA.none { it.content.contains("Chat B") })

        // Switch to Chat B
        viewModel.selectConversation(chatBId)
        testScheduler.advanceUntilIdle()

        val loadedB = viewModel.uiState.value.messages
        assertTrue("Loaded Chat B must have messages", loadedB.isNotEmpty())
        assertTrue("Loaded Chat B must only contain Chat B messages", loadedB.all { it.conversationId == chatBId })
        assertTrue("Loaded Chat B must not contain Chat A messages", loadedB.none { it.content.contains("Chat A") })
    }

    @Test
    fun testStartNewConversationWhileStreamingCancelsActiveInference() = runTest(testDispatcher) {
        backgroundScope.launch { viewModel.uiState.collect { } }
        testScheduler.advanceUntilIdle()

        // Send message
        viewModel.onInputTextChanged("Streaming test prompt")
        viewModel.sendMessage()

        // Start new conversation while generation might be in-flight
        viewModel.startNewConversation()
        testScheduler.advanceUntilIdle()

        assertFalse("Streaming should be terminated", viewModel.uiState.value.isStreaming)
        assertEquals(null, viewModel.uiState.value.currentConversation)
        assertEquals(0, viewModel.uiState.value.messages.size)
    }
}

// --- Test Fakes ---

class FakeConversationRepository : ConversationRepository {
    val conversations = MutableStateFlow<List<Conversation>>(emptyList())
    val allMessages = MutableStateFlow<List<Message>>(emptyList())

    override fun getAllConversations(): Flow<List<Conversation>> = conversations

    override suspend fun getConversationById(id: String): Conversation? =
        conversations.value.find { it.id == id }

    override suspend fun createConversation(conversation: Conversation) {
        conversations.value = conversations.value + conversation
    }

    override suspend fun updateConversation(conversation: Conversation) {
        conversations.value = conversations.value.map { if (it.id == conversation.id) conversation else it }
    }

    override suspend fun deleteConversation(id: String) {
        conversations.value = conversations.value.filter { it.id != id }
        allMessages.value = allMessages.value.filter { it.conversationId != id }
    }

    override fun getMessagesForConversation(conversationId: String): Flow<List<Message>> =
        flow {
            allMessages.collect { list ->
                emit(list.filter { it.conversationId == conversationId })
            }
        }

    override suspend fun sendMessage(message: Message) {
        allMessages.value = allMessages.value + message
    }

    override suspend fun updateMessage(message: Message) {
        allMessages.value = allMessages.value.map { if (it.id == message.id) message else it }
    }

    override suspend fun deleteMessage(id: String) {
        allMessages.value = allMessages.value.filter { it.id != id }
    }

    override suspend fun clearMessagesForConversation(conversationId: String) {
        allMessages.value = allMessages.value.filter { it.conversationId != conversationId }
    }
}

class FakeAgentRepository : AgentRepository {
    private val defaultAgents = listOf(
        Agent(
            id = "agent_supervisor",
            name = "Chief Clinical Supervisor",
            role = AgentRole.SUPERVISOR,
            systemPrompt = "Lead clinician coordinating diagnosis and care plans.",
            isDefault = true
        )
    )

    override fun getAllAgents(): Flow<List<Agent>> = flowOf(defaultAgents)
    override suspend fun getAgentById(id: String): Agent? = defaultAgents.find { it.id == id }
    override suspend fun saveAgent(agent: Agent) {}
    override suspend fun deleteAgent(id: String) {}
    override fun getAllTeams(): Flow<List<com.agentx.domain.models.AgentTeam>> = flowOf(emptyList())
    override suspend fun getTeamById(id: String): com.agentx.domain.models.AgentTeam? = null
    override suspend fun saveTeam(team: com.agentx.domain.models.AgentTeam) {}
    override suspend fun deleteTeam(id: String) {}
}

class FakeModelRepository : ModelRepository {
    val models = MutableStateFlow<List<GGUFModel>>(emptyList())
    val activeModel = MutableStateFlow<GGUFModel?>(null)

    override fun getAllModels(): Flow<List<GGUFModel>> = models
    override fun getActiveModel(): Flow<GGUFModel?> = activeModel
    override suspend fun getModelById(id: String): GGUFModel? = models.value.find { it.id == id }
    override suspend fun addModel(model: GGUFModel) {
        models.value = models.value + model
    }
    override suspend fun updateModel(model: GGUFModel) {
        models.value = models.value.map { if (it.id == model.id) model else it }
    }
    override suspend fun setActiveModel(id: String) {
        activeModel.value = models.value.find { it.id == id }
    }
    override suspend fun deleteModel(id: String) {
        models.value = models.value.filter { it.id != id }
    }
}

class FakeInferenceEngine : InferenceEngine {
    var status = ModelStatus(ModelLifecycleState.NOT_LOADED)
    var stopped = false

    override suspend fun loadModel(config: ModelConfig) {
        status = ModelStatus(
            state = ModelLifecycleState.LOADED,
            activeModelId = config.modelId,
            memoryUsageBytes = 2048L * 1024L * 1024L
        )
    }

    override fun generate(request: InferenceRequest): Flow<GenerationEvent> = flow {
        emit(GenerationEvent.Token("Clinical "))
        emit(GenerationEvent.Token("Inference complete."))
        emit(GenerationEvent.Finished(totalTokens = 4, promptTokens = 10, completionTokens = 4, durationMs = 150))
    }

    override suspend fun stopGeneration() {
        stopped = true
    }

    override suspend fun unloadModel() {
        status = ModelStatus(ModelLifecycleState.NOT_LOADED)
    }

    override fun getModelStatus(): ModelStatus = status
}
