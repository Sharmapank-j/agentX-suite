package com.agentx

import com.agentx.core.agent.core.AgentContext
import com.agentx.core.agent.core.AgentResult
import com.agentx.core.agent.core.ToolCallResult
import com.agentx.core.agent.execution.ExecutionQueue
import com.agentx.core.agent.orchestrator.ClinicalAgentExecutor
import com.agentx.core.agent.orchestrator.ClinicalResultAggregator
import com.agentx.core.agent.orchestrator.ClinicalSupervisor
import com.agentx.core.agent.orchestrator.ClinicalTaskPlanner
import com.agentx.core.agent.orchestrator.ExecutionPlan
import com.agentx.core.agent.orchestrator.HeuristicTaskPlanner
import com.agentx.core.agent.orchestrator.PlanParser
import com.agentx.core.agent.orchestrator.SubTask
import com.agentx.core.agent.tools.ClinicalToolDefinitions
import com.agentx.core.agent.tools.ClinicalToolExecutor
import com.agentx.core.agent.tools.ToolRegistry
import com.agentx.data.repository.ExecutionRepository
import com.agentx.domain.models.AgentRole
import com.agentx.domain.models.ExecutionLog
import com.agentx.domain.models.ExecutionStatus
import com.agentx.feature.chat.ChatViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

// ─── Fake Execution Repository ────────────────────────────────────────────────

class FakeExecutionRepository : ExecutionRepository {
    val logs = MutableStateFlow<List<ExecutionLog>>(emptyList())

    override fun getExecutionsForConversation(conversationId: String): Flow<List<ExecutionLog>> =
        MutableStateFlow(logs.value.filter { it.conversationId == conversationId })

    override fun getRecentExecutions(): Flow<List<ExecutionLog>> = logs

    override suspend fun logExecution(log: ExecutionLog) {
        logs.value = logs.value + log
    }

    override suspend fun updateExecution(log: ExecutionLog) {
        logs.value = logs.value.map { if (it.id == log.id) log else it }
    }
}

// ─── Plan Parser Tests ────────────────────────────────────────────────────────

class PlanParserTest {

    @Test
    fun `parses clean JSON execution plan`() {
        val json = """
            {
              "originalGoal": "Evaluate AF stroke risk",
              "subtasks": [
                {
                  "id": "t1",
                  "title": "Calculate CHA2DS2-VASc",
                  "assignedRole": "CODER",
                  "instructions": "Execute risk tool",
                  "dependencies": []
                }
              ]
            }
        """.trimIndent()

        val plan = PlanParser.parse(json)
        assertNotNull("Plan should not be null", plan)
        assertEquals("Evaluate AF stroke risk", plan?.originalGoal)
        assertEquals(1, plan?.subtasks?.size)
        assertEquals(AgentRole.CODER, plan?.subtasks?.get(0)?.assignedRole)
    }

    @Test
    fun `parses plan wrapped in markdown code fence`() {
        val wrapped = """
            Here is the clinical execution plan:
            ```json
            {
              "originalGoal": "Cardiac workup",
              "subtasks": [
                {
                  "id": "t1",
                  "title": "Analyze Labs",
                  "assignedRole": "ANALYST",
                  "instructions": "Correlate troponin",
                  "dependencies": []
                },
                {
                  "id": "t2",
                  "title": "Safety Audit",
                  "assignedRole": "REVIEWER",
                  "instructions": "Check contraindications",
                  "dependencies": ["t1"]
                }
              ]
            }
            ```
            Proceeding with coordination.
        """.trimIndent()

        val plan = PlanParser.parse(wrapped)
        assertNotNull("Plan should parse from code fence", plan)
        assertEquals(2, plan?.subtasks?.size)
        assertEquals(listOf("t1"), plan?.subtasks?.get(1)?.dependencies)
    }

    @Test
    fun `returns null for malformed or non-JSON text`() {
        val nonJson = "I am a clinical assistant and cannot output JSON right now."
        val plan = PlanParser.parse(nonJson)
        assertNull("Should return null for non-JSON", plan)
    }
}

// ─── Heuristic Task Planner Tests ─────────────────────────────────────────────

class HeuristicTaskPlannerTest {

    private val planner = HeuristicTaskPlanner()
    private val allRoles = AgentRole.entries

    @Test
    fun `dosing query includes PLANNER, CODER, and REVIEWER`() = runTest {
        val plan = planner.createPlan("Calculate warfarin and aspirin bleeding risk in CKD", allRoles)
        val roles = plan.subtasks.map { it.assignedRole }

        assertTrue("Must include PLANNER", roles.contains(AgentRole.PLANNER))
        assertTrue("Must include CODER for dosing/calculator", roles.contains(AgentRole.CODER))
        assertTrue("Must include REVIEWER for safety audit", roles.contains(AgentRole.REVIEWER))
    }

    @Test
    fun `lab query includes ANALYST and REVIEWER`() = runTest {
        val plan = planner.createPlan("Interpret abnormal potassium and troponin lab panel", allRoles)
        val roles = plan.subtasks.map { it.assignedRole }

        assertTrue("Must include ANALYST", roles.contains(AgentRole.ANALYST))
        assertTrue("Must include REVIEWER", roles.contains(AgentRole.REVIEWER))
    }

    @Test
    fun `cardiovascular physiology curriculum includes full multi-agent pipeline`() = runTest {
        val plan = planner.createPlan("Build a study plan for cardiovascular physiology", allRoles)
        val roles = plan.subtasks.map { it.assignedRole }

        assertTrue("Should include PLANNER", roles.contains(AgentRole.PLANNER))
        assertTrue("Should include RESEARCHER", roles.contains(AgentRole.RESEARCHER))
        assertTrue("Should include ANALYST", roles.contains(AgentRole.ANALYST))
        assertTrue("Should include REVIEWER", roles.contains(AgentRole.REVIEWER))
    }

    @Test
    fun `reviewer subtask depends on prior subtasks`() = runTest {
        val plan = planner.createPlan("Evaluate hypertension guidelines and medications", allRoles)
        val reviewerTask = plan.subtasks.firstOrNull { it.assignedRole == AgentRole.REVIEWER }

        assertNotNull("Reviewer task must exist", reviewerTask)
        assertTrue("Reviewer should depend on earlier subtasks", reviewerTask!!.dependencies.isNotEmpty())
    }
}

// ─── Execution Queue Tests ────────────────────────────────────────────────────

class ExecutionQueueTest {

    @Test
    fun `initial state is QUEUED`() {
        val queue = ExecutionQueue()
        assertEquals(ExecutionStatus.QUEUED, queue.state.value.status)
        assertEquals(0, queue.state.value.currentStep)
    }

    @Test
    fun `updateProgress transitions to RUNNING with step details`() {
        val queue = ExecutionQueue()
        queue.updateProgress(
            conversationId = "conv_123",
            step = 2,
            total = 4,
            role = AgentRole.CODER,
            agentId = "agent_coder",
            activity = "Calculating eGFR"
        )

        val state = queue.state.value
        assertEquals(ExecutionStatus.RUNNING, state.status)
        assertEquals(2, state.currentStep)
        assertEquals(4, state.totalSteps)
        assertEquals(AgentRole.CODER, state.activeRole)
        assertEquals("Calculating eGFR", state.currentActivity)
    }

    @Test
    fun `complete transitions to COMPLETED`() {
        val queue = ExecutionQueue()
        queue.updateProgress("conv_123", 1, 2, AgentRole.PLANNER, "agent_planner", "Planning")
        queue.complete("conv_123", "Workflow finished")

        assertEquals(ExecutionStatus.COMPLETED, queue.state.value.status)
        assertTrue(queue.state.value.currentActivity.contains("finished"))
    }

    @Test
    fun `fail transitions to FAILED`() {
        val queue = ExecutionQueue()
        queue.fail("conv_123", "Timeout occurred")

        assertEquals(ExecutionStatus.FAILED, queue.state.value.status)
        assertTrue(queue.state.value.currentActivity.contains("Timeout occurred"))
    }

    @Test
    fun `reset returns queue to initial state`() {
        val queue = ExecutionQueue()
        queue.updateProgress("conv_123", 1, 2, AgentRole.PLANNER, "agent_planner", "Planning")
        queue.reset()

        assertEquals(ExecutionStatus.QUEUED, queue.state.value.status)
        assertEquals(0, queue.state.value.currentStep)
    }
}

// ─── Clinical Result Aggregator Tests ─────────────────────────────────────────

class ClinicalResultAggregatorTest {

    @Test
    fun `synthesizes fallback report containing all specialist results and disclaimer`() = runTest {
        val aggregator = ClinicalResultAggregator(inferenceEngine = null)
        val results = listOf(
            AgentResult(
                agentId = "agent_planner",
                taskId = "subtask_plan",
                output = "Plan: 1. Evaluate renal function. 2. Check contraindications.",
                executionStatus = "COMPLETED"
            ),
            AgentResult(
                agentId = "agent_coder",
                taskId = "subtask_coder",
                output = "eGFR is 42 mL/min/1.73 m² (Stage G3b). Dose reduction recommended.",
                toolCalls = listOf(ToolCallResult("tool_clinical_calc", "eGFR: 42", true)),
                executionStatus = "COMPLETED"
            )
        )

        val finalReport = aggregator.synthesize("Evaluate renal safety", results)

        assertTrue("Must include Assessment header", finalReport.contains("Clinical Multi-Agent Assessment"))
        assertTrue("Must include planner output", finalReport.contains("Evaluate renal function"))
        assertTrue("Must include coder output", finalReport.contains("Stage G3b"))
        assertTrue("Must mention verified tool execution", finalReport.contains("tool_clinical_calc"))
        assertTrue("Must include physician judgment disclaimer", finalReport.contains("Clinical Decision Support"))
    }
}

// ─── Multi-Agent Supervisor Coordination Tests ────────────────────────────────

@OptIn(ExperimentalCoroutinesApi::class)
class ClinicalSupervisorCoordinationTest {

    private lateinit var toolRegistry: ToolRegistry
    private lateinit var toolExecutor: ClinicalToolExecutor
    private lateinit var fakeInference: FakeInferenceEngine
    private lateinit var executionRepo: FakeExecutionRepository
    private lateinit var executionQueue: ExecutionQueue

    @Before
    fun setUp() {
        toolRegistry = ToolRegistry()
        ClinicalToolDefinitions.all.forEach { toolRegistry.register(it) }
        toolExecutor = ClinicalToolExecutor()
        fakeInference = FakeInferenceEngine()
        executionRepo = FakeExecutionRepository()
        executionQueue = ExecutionQueue()
    }

    @Test
    fun `supervisor coordinates full multi-agent pipeline and logs executions`() = runTest {
        val agentExecutors: Map<AgentRole, ClinicalAgentExecutor> = AgentRole.entries
            .filter { it != AgentRole.SUPERVISOR }
            .associate { role ->
                role to ClinicalAgentExecutor(
                    id = "agent_${role.name.lowercase()}",
                    name = role.title,
                    role = role,
                    systemPrompt = role.description,
                    inferenceEngine = fakeInference,
                    toolRegistry = toolRegistry,
                    toolExecutor = toolExecutor
                )
            }

        val supervisor = ClinicalSupervisor(
            agents = agentExecutors,
            inferenceEngine = fakeInference,
            taskPlanner = HeuristicTaskPlanner(),
            resultAggregator = ClinicalResultAggregator(null),
            executionRepository = executionRepo,
            executionQueue = executionQueue
        )

        val result = supervisor.coordinate(
            userGoal = "Evaluate warfarin dosing and stroke risk for 70yo AF patient",
            conversationId = "conv_test_af"
        )

        // 1. Final result assertions
        assertEquals("agent_supervisor", result.agentId)
        assertEquals("COMPLETED", result.executionStatus)
        assertTrue("Output should contain clinical report", result.output.contains("Clinical Multi-Agent Assessment"))

        // 2. ExecutionQueue assertions
        assertEquals("Queue should be marked COMPLETED", ExecutionStatus.COMPLETED, executionQueue.state.value.status)

        // 3. ExecutionRepository persistence assertions
        val recordedLogs = executionRepo.logs.value
        assertTrue("Should have recorded multiple subtask logs", recordedLogs.size >= 3)
        assertTrue("Should contain a synthesis log", recordedLogs.any { it.taskId.contains("synthesis") })
        recordedLogs.forEach { log ->
            assertEquals("conv_test_af", log.conversationId)
            assertEquals(ExecutionStatus.COMPLETED, log.status)
        }
    }
}

// ─── ChatViewModel Multi-Agent Integration Tests ──────────────────────────────

@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModelMultiAgentTest {

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var conversationRepo: FakeConversationRepository
    private lateinit var agentRepo: FakeAgentRepository
    private lateinit var modelRepo: FakeModelRepository
    private lateinit var inferenceEngine: FakeInferenceEngine
    private lateinit var executionRepo: FakeExecutionRepository
    private lateinit var executionQueue: ExecutionQueue
    private lateinit var supervisor: ClinicalSupervisor
    private lateinit var viewModel: ChatViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        conversationRepo = FakeConversationRepository()
        agentRepo = FakeAgentRepository()
        modelRepo = FakeModelRepository()
        inferenceEngine = FakeInferenceEngine()
        executionRepo = FakeExecutionRepository()
        executionQueue = ExecutionQueue()

        val toolRegistry = ToolRegistry()
        ClinicalToolDefinitions.all.forEach { toolRegistry.register(it) }
        val toolExecutor = ClinicalToolExecutor()

        val agents = AgentRole.entries.filter { it != AgentRole.SUPERVISOR }.associate { role ->
            role to ClinicalAgentExecutor(
                id = "agent_${role.name.lowercase()}",
                name = role.title,
                role = role,
                systemPrompt = role.description,
                inferenceEngine = inferenceEngine,
                toolRegistry = toolRegistry,
                toolExecutor = toolExecutor
            )
        }

        supervisor = ClinicalSupervisor(
            agents = agents,
            inferenceEngine = inferenceEngine,
            taskPlanner = HeuristicTaskPlanner(),
            resultAggregator = ClinicalResultAggregator(null),
            executionRepository = executionRepo,
            executionQueue = executionQueue
        )

        viewModel = ChatViewModel(
            conversationRepository = conversationRepo,
            agentRepository = agentRepo,
            modelRepository = modelRepo,
            inferenceEngine = inferenceEngine,
            clinicalSupervisor = supervisor,
            executionQueue = executionQueue
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `sending message to supervisor executes multi-agent pipeline and delivers synthesis`() = runTest(testDispatcher) {
        backgroundScope.launch { viewModel.uiState.collect { } }
        testScheduler.advanceUntilIdle()

        viewModel.createNewConversation("AF Consult")
        testScheduler.advanceUntilIdle()

        viewModel.onInputTextChanged("Review CHA2DS2-VASc score and anticoagulant safety")
        viewModel.sendMessage()
        testScheduler.advanceUntilIdle()

        val messages = conversationRepo.allMessages.value
        assertEquals("Should have User and Assistant messages", 2, messages.size)

        val assistantMessage = messages.first { it.role == com.agentx.domain.models.MessageRole.ASSISTANT }
        assertEquals(com.agentx.domain.models.MessageStatus.COMPLETED, assistantMessage.status)
        assertTrue("Assistant message should contain assessment", assistantMessage.content.contains("Clinical Multi-Agent Assessment"))

        // Verify that execution logs were stored in the repository
        val logs = executionRepo.logs.value
        assertTrue("Executions must be persisted", logs.isNotEmpty())
    }
}
