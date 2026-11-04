package com.agentx

import com.agentx.core.agent.core.AgentContext
import com.agentx.core.agent.execution.ExecutionQueue
import com.agentx.core.agent.execution.ExecutionState
import com.agentx.core.agent.prompts.PromptManager
import com.agentx.core.agent.tools.ToolDefinition
import com.agentx.core.agent.tools.ToolRegistry
import com.agentx.domain.models.Agent
import com.agentx.domain.models.AgentRole
import com.agentx.domain.models.Conversation
import com.agentx.domain.models.ExecutionStatus
import com.agentx.domain.models.GGUFModel
import com.agentx.domain.models.Message
import com.agentx.domain.models.MessageRole
import com.agentx.domain.models.MessageStatus
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DomainAndRuntimeTests {

    @Test
    fun testAgentXRoleProperties() {
        val supervisor = AgentRole.SUPERVISOR
        assertEquals("Chief Orchestrator & Supervisor", supervisor.title)
        assertTrue(supervisor.description.contains("multi-agent workflows"))

        val coder = AgentRole.CODER
        assertEquals("Code & Systems Architect", coder.title)
        assertTrue(coder.description.contains("high-performance code"))

        val reviewer = AgentRole.REVIEWER
        assertEquals("Quality & Safety Reviewer", reviewer.title)
        assertTrue(reviewer.description.contains("correctness"))

        val assistant = AgentRole.ASSISTANT
        assertEquals("AgentX General Assistant", assistant.title)
        assertTrue(assistant.description.contains("ChatGPT style"))
    }

    @Test
    fun testMedicalAgentCreation() {
        val agent = Agent(
            id = "agent_cardio_analyst",
            name = "Cardiology Pathophysiology Analyst",
            role = AgentRole.ANALYST,
            systemPrompt = "Analyze cardiac electrophysiology and hemodynamic parameters.",
            temperature = 0.2f,
            topP = 0.95f,
            maxTokens = 2048,
            isDefault = true
        )

        assertEquals("agent_cardio_analyst", agent.id)
        assertEquals(AgentRole.ANALYST, agent.role)
        assertEquals(0.2f, agent.temperature, 0.001f)
        assertTrue(agent.isDefault)
    }

    @Test
    fun testClinicalToolRegistry() {
        val registry = ToolRegistry()
        val calcTool = ToolDefinition(
            id = "tool_clinical_calc",
            name = "Clinical Risk & Dosage Calculator",
            description = "Calculates eGFR and CHA2DS2-VASc scores",
            parametersJsonSchema = "{\"type\": \"object\"}"
        )
        registry.register(calcTool)

        val retrieved = registry.get("tool_clinical_calc")
        assertNotNull(retrieved)
        assertEquals("Clinical Risk & Dosage Calculator", retrieved?.name)
        assertEquals(1, registry.getAll().size)
    }

    @Test
    fun testAgentXPromptManager() {
        val roles = listOf(AgentRole.PLANNER, AgentRole.RESEARCHER, AgentRole.CODER)
        val prompt = PromptManager.buildSupervisorPrompt(roles)

        assertTrue(prompt.contains("Task & Strategy Planner"))
        assertTrue(prompt.contains("Deep Researcher & Fact-Finder"))
        assertTrue(prompt.contains("Code & Systems Architect"))
        assertTrue(prompt.contains("decompose"))

        val customPrompt = PromptManager.buildAgentSystemPrompt(
            role = AgentRole.REVIEWER,
            customInstructions = "Verify all calculations and potential security edge cases."
        )
        assertTrue(customPrompt.contains("Quality & Safety Reviewer"))
        assertTrue(customPrompt.contains("Verify all calculations"))
    }

    @Test
    fun testExecutionQueueStateFlow() = runTest {
        val queue = ExecutionQueue()
        val initialState = queue.state.first()
        assertEquals(ExecutionStatus.QUEUED, initialState.status)

        val runningState = ExecutionState(
            conversationId = "conv_cardio_01",
            activeAgentId = "agent_coder",
            currentStep = 1,
            totalSteps = 5,
            status = ExecutionStatus.RUNNING,
            currentActivity = "Computing CHA2DS2-VASc stroke risk score"
        )
        queue.updateState(runningState)

        val updatedState = queue.state.first()
        assertEquals(ExecutionStatus.RUNNING, updatedState.status)
        assertEquals("conv_cardio_01", updatedState.conversationId)
        assertEquals("Computing CHA2DS2-VASc stroke risk score", updatedState.currentActivity)
    }

    @Test
    fun testMedicalContextSafeguards() {
        val context = AgentContext(
            conversationId = "conv_physio_plan",
            taskId = "task_cardiovascular_study",
            userGoal = "Build a study plan for cardiovascular physiology",
            currentIteration = 1,
            maxIterations = 5,
            isCancelled = false
        )

        assertEquals("conv_physio_plan", context.conversationId)
        assertEquals("Build a study plan for cardiovascular physiology", context.userGoal)
        assertFalse(context.isCancelled)
    }

    @Test
    fun testMedicalGGUFModelDomainEntity() {
        val model = GGUFModel(
            id = "model_biomistral_7b",
            name = "BioMistral-7B-Q4_K_M",
            filePath = "/storage/emulated/0/Download/biomistral-7b-q4_k_m.gguf",
            uriString = "content://media/external/file/54321",
            sizeBytes = 4325600000L,
            contextLength = 4096,
            quantization = "Q4_K_M",
            capabilityProfile = "medical-clinical"
        )

        assertEquals("BioMistral-7B-Q4_K_M", model.name)
        assertEquals("medical-clinical", model.capabilityProfile)
        assertEquals("Q4_K_M", model.quantization)
    }
}
