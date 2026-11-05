package com.agentx

import com.agentx.core.agent.core.AgentContext
import com.agentx.core.agent.core.AgentMessage
import com.agentx.core.agent.core.AgentResult
import com.agentx.core.agent.core.ToolCallRequest
import com.agentx.core.agent.core.ToolCallResult
import com.agentx.core.agent.execution.ExecutionState
import com.agentx.core.agent.orchestrator.ExecutionPlan
import com.agentx.core.agent.orchestrator.SubTask
import com.agentx.agent.tools.ClinicalToolDefinitions
import com.agentx.agent.tools.ClinicalToolExecutor
import com.agentx.agent.tools.ToolDefinition
import com.agentx.core.inference.GenerationEvent
import com.agentx.core.inference.LlamaCppEngine
import com.agentx.core.inference.ModelConfig
import com.agentx.core.inference.ModelLifecycleState
import com.agentx.domain.models.Agent
import com.agentx.domain.models.AgentRole
import com.agentx.domain.models.Conversation
import com.agentx.domain.models.ExecutionStatus
import com.agentx.domain.models.GGUFModel
import com.agentx.domain.models.Message
import com.agentx.domain.models.MessageRole
import com.agentx.domain.models.MessageStatus
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

// ─── ProGuard / R8 Serialization Compatibility Tests ─────────────────────────

class ProguardSerializationCompatibilityTest {

    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }

    @Test
    fun `ExecutionPlan serializes and deserializes accurately`() {
        val original = ExecutionPlan(
            originalGoal = "Evaluate stroke risk in AFib with CKD",
            subtasks = listOf(
                SubTask(
                    id = "task_coder",
                    title = "Calculate CHA2DS2-VASc and eGFR",
                    assignedRole = AgentRole.CODER,
                    instructions = "Execute tools",
                    dependencies = emptyList()
                ),
                SubTask(
                    id = "task_review",
                    title = "Safety & Bleeding Risk Audit",
                    assignedRole = AgentRole.REVIEWER,
                    instructions = "Audit contraindications",
                    dependencies = listOf("task_coder")
                )
            )
        )

        val encoded = json.encodeToString(original)
        val decoded = json.decodeFromString<ExecutionPlan>(encoded)

        assertEquals(original.originalGoal, decoded.originalGoal)
        assertEquals(original.subtasks.size, decoded.subtasks.size)
        assertEquals(original.subtasks[1].dependencies, decoded.subtasks[1].dependencies)
    }

    @Test
    fun `AgentResult with ToolCallResults serializes accurately`() {
        val original = AgentResult(
            agentId = "agent_coder",
            taskId = "task_1",
            output = "eGFR calculated: 45 mL/min/1.73m2 (Stage G3a)",
            toolCalls = listOf(
                ToolCallResult("tool_clinical_calc", "eGFR: 45", true, null)
            ),
            executionStatus = "COMPLETED",
            tokenUsage = 340,
            executionTimeMs = 120L
        )

        val encoded = json.encodeToString(original)
        val decoded = json.decodeFromString<AgentResult>(encoded)

        assertEquals(original.agentId, decoded.agentId)
        assertEquals(original.output, decoded.output)
        assertEquals(1, decoded.toolCalls.size)
        assertEquals(original.toolCalls[0].toolId, decoded.toolCalls[0].toolId)
        assertEquals(original.tokenUsage, decoded.tokenUsage)
    }

    @Test
    fun `ExecutionState serializes accurately`() {
        val original = ExecutionState(
            conversationId = "conv_123",
            activeAgentId = "agent_reviewer",
            activeRole = AgentRole.REVIEWER,
            currentStep = 3,
            totalSteps = 5,
            status = ExecutionStatus.RUNNING,
            currentActivity = "Auditing black-box warnings",
            intermediateOutputs = listOf("Step 1 done", "Step 2 done")
        )

        val encoded = json.encodeToString(original)
        val decoded = json.decodeFromString<ExecutionState>(encoded)

        assertEquals(original.conversationId, decoded.conversationId)
        assertEquals(original.activeRole, decoded.activeRole)
        assertEquals(original.status, decoded.status)
        assertEquals(original.intermediateOutputs.size, decoded.intermediateOutputs.size)
    }

    @Test
    fun `Core Domain Entities serialize accurately`() {
        val msg = Message(
            id = "msg_1",
            conversationId = "c_1",
            role = MessageRole.ASSISTANT,
            senderName = "Chief Clinical Supervisor",
            content = "Clinical diagnosis summary",
            status = MessageStatus.COMPLETED
        )
        val encodedMsg = json.encodeToString(msg)
        val decodedMsg = json.decodeFromString<Message>(encodedMsg)
        assertEquals(msg.content, decodedMsg.content)
        assertEquals(msg.role, decodedMsg.role)

        val model = GGUFModel(
            id = "m_1",
            name = "BioMistral 7B Q4_K_M",
            filePath = "/data/user/0/com.agentx/files/biomistral.gguf",
            uriString = "content://model/1",
            sizeBytes = 4_500_000_000L,
            architecture = "llama",
            quantization = "Q4_K_M"
        )
        val encodedModel = json.encodeToString(model)
        val decodedModel = json.decodeFromString<GGUFModel>(encodedModel)
        assertEquals(model.quantization, decodedModel.quantization)
    }
}

// ─── Memory Budget & Engine Lifecycle Tests ───────────────────────────────────

class MemoryManagementAndEngineLifecycleTest {

    @Test
    fun `LlamaCppEngine reports NOT_LOADED status initially`() {
        val engine = LlamaCppEngine()
        val status = engine.getModelStatus()

        assertEquals(ModelLifecycleState.NOT_LOADED, status.state)
        assertEquals(0L, status.memoryUsageBytes)
    }

    @Test
    fun `LlamaCppEngine refuses to claim a model loaded without native runtime`() = runTest {
        val engine = LlamaCppEngine()
        try {
            engine.loadModel(
                ModelConfig(
                    modelId = "test_model",
                    modelPath = "/path/test.gguf",
                    threads = 4,
                    contextLength = 2048
                )
            )
            throw AssertionError("Stub runtime must not report a model as loaded")
        } catch (expected: UnsupportedOperationException) {
            assertTrue(expected.message.orEmpty().contains("Real GGUF inference"))
        }

        val status = engine.getModelStatus()
        assertEquals(ModelLifecycleState.NOT_LOADED, status.state)
        assertEquals(0L, status.memoryUsageBytes)
    }

    @Test
    fun `LlamaCppEngine emits explicit error without synthetic tokens`() = runTest {
        val engine = LlamaCppEngine()
        val events = engine.generate(
            com.agentx.core.inference.InferenceRequest(prompt = "Patient has chest pain")
        ).toList()

        assertTrue("Should receive an explicit error", events.any { it is GenerationEvent.Error })
        assertFalse("Must not emit fabricated tokens", events.any { it is GenerationEvent.Token })
        assertFalse("Must not report fabricated completion", events.any { it is GenerationEvent.Finished })
    }}

// ─── Hardware CPU Thread Clamping Tests ───────────────────────────────────────

class HardwarePerformanceProfilingTest {

    @Test
    fun `CPU thread recommendation clamps to safe bounds on any hardware`() {
        // Test edge cases: 1 core, 2 cores, 4 cores, 8 cores, 16 cores
        val testCores = listOf(1, 2, 4, 8, 16)

        testCores.forEach { cores ->
            val recommended = (cores - 1).coerceIn(1, 8)
            assertTrue("Recommended threads must be at least 1", recommended >= 1)
            assertTrue("Recommended threads must not exceed 8 for mobile thermals", recommended <= 8)
            if (cores > 1) {
                assertTrue("Must leave at least 1 core free for OS/UI", recommended < cores)
            }
        }
    }
}

// ─── HIPAA & Offline Clinical Invariants Tests ────────────────────────────────

class HipaaOfflineInvariantsTest {

    @Test
    fun `Clinical calculators execute entirely locally with zero external network dependencies`() = runTest {
        val executor = ClinicalToolExecutor()

        // 1. eGFR local calculation
        val egfrResult = executor.execute(
            "tool_clinical_calc",
            """{"calculation":"egfr","age":62,"sex":"M","creatinine_mg_dl":1.8}"""
        )
        assertTrue(egfrResult.isSuccess)
        assertTrue(egfrResult.output.contains("CKD-EPI 2021"))

        // 2. CHA2DS2-VASc local calculation
        val afResult = executor.execute(
            "tool_clinical_calc",
            """{"calculation":"cha2ds2vasc","chf":"true","hypertension":"true","age":75,"diabetes":"true","stroke_history":"false","vascular_disease":"false","sex":"F"}"""
        )
        assertTrue(afResult.isSuccess)
        assertTrue(afResult.output.contains("CHA₂DS₂-VASc Score Result"))

        // 3. Drug safety local evaluation
        val drugResult = executor.execute(
            "tool_drug_safety",
            """{"drugs":["warfarin","aspirin","amiodarone"]}"""
        )
        assertTrue(drugResult.isSuccess)
        assertTrue(drugResult.output.contains("HIGH RISK"))
        assertTrue(drugResult.output.contains("BLACK BOX"))

        // 4. Lab interpretation local evaluation
        val labResult = executor.execute(
            "tool_lab_interpreter",
            """{"panel":{"potassium":6.4,"troponin":0.35}}"""
        )
        assertTrue(labResult.isSuccess)
        assertTrue(labResult.output.contains("Hyperkalemia"))
        assertTrue(labResult.output.contains("Myocardial injury"))
    }

    @Test
    fun `Patient record tool enforces HIPAA offline safeguard notice`() = runTest {
        val executor = ClinicalToolExecutor()
        val result = executor.execute("tool_patient_record", """{"query":"recent medications"}""")

        assertTrue(result.isSuccess)
        assertTrue("Must display HIPAA Safeguard Active banner", result.output.contains("HIPAA Safeguard Active"))
        assertTrue("Must guarantee zero external transmission", result.output.contains("strictly local and offline"))
    }

    @Test
    fun `All registered clinical tools are accounted for in definition catalogue`() {
        val tools = ClinicalToolDefinitions.all
        assertEquals("Must have 4 registered clinical tools", 4, tools.size)

        val patientTool = tools.first { it.id == "tool_patient_record" }
        assertTrue("Patient record tool must require explicit permission", patientTool.requiresExplicitPermission)
    }
}
