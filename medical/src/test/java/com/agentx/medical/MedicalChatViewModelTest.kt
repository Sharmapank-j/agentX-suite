package com.agentx.medical

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
import com.agentx.medical.domain.models.GGUFModel
import com.agentx.medical.domain.models.Message
import com.agentx.medical.domain.models.MessageRole
import com.agentx.medical.domain.models.MessageStatus
import com.agentx.medical.feature.chat.ChatViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
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
class MedicalChatViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var conversationRepo: FakeMedicalConversationRepository
    private lateinit var agentRepo: FakeMedicalAgentRepository
    private lateinit var modelRepo: FakeMedicalModelRepository
    private lateinit var inferenceEngine: FakeMedicalInferenceEngine
    private lateinit var viewModel: ChatViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        conversationRepo = FakeMedicalConversationRepository()
        agentRepo = FakeMedicalAgentRepository()
        modelRepo = FakeMedicalModelRepository()
        inferenceEngine = FakeMedicalInferenceEngine()

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
    fun testStartNewConsultationSeparatesMessagesFromPreviousConsultation() = runTest(testDispatcher) {
        backgroundScope.launch { viewModel.uiState.collect { } }
        testScheduler.advanceUntilIdle()

        // Consultation 1
        viewModel.onInputTextChanged("Patient has acute chest pain radiating to left arm")
        viewModel.sendMessage()
        testScheduler.advanceUntilIdle()

        val conv1Id = viewModel.uiState.value.currentConversation?.id
        assertNotNull("Consultation 1 should have an ID", conv1Id)
        val conv1Messages = viewModel.uiState.value.messages
        assertEquals(2, conv1Messages.size)
        assertTrue(conv1Messages.any { it.content.contains("acute chest pain") })

        // Start New Consultation
        viewModel.startNewConversation()
        testScheduler.advanceUntilIdle()

        assertEquals(null, viewModel.uiState.value.currentConversation)
        assertEquals(0, viewModel.uiState.value.messages.size)

        // Consultation 2
        viewModel.onInputTextChanged("Recommend pediatric amoxicillin dosing")
        viewModel.sendMessage()
        testScheduler.advanceUntilIdle()

        val conv2Id = viewModel.uiState.value.currentConversation?.id
        assertNotNull("Consultation 2 should have an ID", conv2Id)
        assertTrue("Consultation 2 ID must be distinct from Consultation 1", conv1Id != conv2Id)

        val conv2Messages = viewModel.uiState.value.messages
        assertEquals(2, conv2Messages.size)
        assertTrue("Consultation 2 messages must contain pediatric dosing",
            conv2Messages.any { it.content.contains("pediatric amoxicillin dosing") })
        assertTrue("Consultation 2 messages must NOT contain chest pain from Consultation 1",
            conv2Messages.none { it.content.contains("chest pain") })
    }

    @Test
    fun testSwitchingConsultationsLoadsOnlyTargetMessages() = runTest(testDispatcher) {
        backgroundScope.launch { viewModel.uiState.collect { } }
        testScheduler.advanceUntilIdle()

        // Consult A
        viewModel.onInputTextChanged("Cardiology Consult A")
        viewModel.sendMessage()
        testScheduler.advanceUntilIdle()
        val consultAId = viewModel.uiState.value.currentConversation!!.id

        // Consult B
        viewModel.startNewConversation()
        testScheduler.advanceUntilIdle()
        viewModel.onInputTextChanged("Neurology Consult B")
        viewModel.sendMessage()
        testScheduler.advanceUntilIdle()
        val consultBId = viewModel.uiState.value.currentConversation!!.id

        // Switch to Consult A
        viewModel.selectConversation(consultAId)
        testScheduler.advanceUntilIdle()

        val loadedA = viewModel.uiState.value.messages
        assertTrue(loadedA.isNotEmpty())
        assertTrue("All loaded messages must belong to Consult A", loadedA.all { it.conversationId == consultAId })
        assertTrue("Loaded Consult A must not contain Consult B content", loadedA.none { it.content.contains("Neurology") })

        // Switch to Consult B
        viewModel.selectConversation(consultBId)
        testScheduler.advanceUntilIdle()

        val loadedB = viewModel.uiState.value.messages
        assertTrue(loadedB.isNotEmpty())
        assertTrue("All loaded messages must belong to Consult B", loadedB.all { it.conversationId == consultBId })
        assertTrue("Loaded Consult B must not contain Consult A content", loadedB.none { it.content.contains("Cardiology") })
    }

    @Test
    fun testStartNewConversationCancelsActiveStreaming() = runTest(testDispatcher) {
        backgroundScope.launch { viewModel.uiState.collect { } }
        testScheduler.advanceUntilIdle()

        viewModel.onInputTextChanged("Complex multi-factor clinical diagnosis")
        viewModel.sendMessage()

        viewModel.startNewConversation()
        testScheduler.advanceUntilIdle()

        assertFalse("Streaming should be terminated", viewModel.uiState.value.isStreaming)
        assertEquals(null, viewModel.uiState.value.currentConversation)
        assertEquals(0, viewModel.uiState.value.messages.size)
    }
}

// --- Test Fakes ---

class FakeMedicalConversationRepository : ConversationRepository {
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

class FakeMedicalAgentRepository : AgentRepository {
    private val defaultAgents = listOf(
        Agent(
            id = "agent_clinical_lead",
            name = "Clinical Consultant Lead",
            role = AgentRole.SUPERVISOR,
            systemPrompt = "Lead physician evaluating clinical inquiries.",
            isDefault = true
        )
    )

    override fun getAllAgents(): Flow<List<Agent>> = flowOf(defaultAgents)
    override suspend fun getAgentById(id: String): Agent? = defaultAgents.find { it.id == id }
    override suspend fun saveAgent(agent: Agent) {}
    override suspend fun deleteAgent(id: String) {}
    override fun getAllTeams(): Flow<List<com.agentx.medical.domain.models.AgentTeam>> = flowOf(emptyList())
    override suspend fun getTeamById(id: String): com.agentx.medical.domain.models.AgentTeam? = null
    override suspend fun saveTeam(team: com.agentx.medical.domain.models.AgentTeam) {}
    override suspend fun deleteTeam(id: String) {}
}

class FakeMedicalModelRepository : ModelRepository {
    val activeModel = MutableStateFlow<GGUFModel?>(
        GGUFModel(
            id = "med_test_model",
            name = "MedTest 7B",
            filePath = "",
            uriString = "content://models/test.gguf",
            sizeBytes = 1000L,
            contextLength = 2048,
            isActive = true
        )
    )
    val models = MutableStateFlow<List<GGUFModel>>(emptyList())

    override fun getActiveModel(): Flow<GGUFModel?> = activeModel
    override fun getAllModels(): Flow<List<GGUFModel>> = models
    override suspend fun getModelById(id: String): GGUFModel? =
        models.value.find { it.id == id } ?: activeModel.value?.takeIf { it.id == id }

    override suspend fun setActiveModel(modelId: String) {
        val model = getModelById(modelId)
        activeModel.value = model
    }

    override suspend fun addModel(model: GGUFModel) {
        models.value = models.value + model
    }

    override suspend fun updateModel(model: GGUFModel) {
        models.value = models.value.map { if (it.id == model.id) model else it }
    }

    override suspend fun deleteModel(id: String) {
        models.value = models.value.filter { it.id != id }
    }
}

class FakeMedicalInferenceEngine : InferenceEngine {
    var status = ModelStatus(ModelLifecycleState.LOADED)
    var stopped = false

    override fun generate(request: InferenceRequest): Flow<GenerationEvent> = flow {
        stopped = false
        emit(GenerationEvent.Token("Clinical analysis: "))
        emit(GenerationEvent.Token("Recommendation complete."))
        emit(GenerationEvent.Finished(totalTokens = 10, promptTokens = 5, completionTokens = 5, durationMs = 100L))
    }

    override suspend fun loadModel(config: ModelConfig) {
        status = ModelStatus(ModelLifecycleState.LOADED, activeModelId = config.modelId)
    }

    override suspend fun unloadModel() {
        status = ModelStatus(ModelLifecycleState.NOT_LOADED)
    }

    override suspend fun stopGeneration() {
        stopped = true
    }

    override fun getModelStatus(): ModelStatus = status
}
