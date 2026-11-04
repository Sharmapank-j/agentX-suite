package com.agentx.admin

import com.agentx.admin.models.LocalModelStatus
import com.agentx.admin.models.ServingMode
import com.agentx.admin.server.AgentXAdminServer
import com.agentx.admin.server.ClientManager
import com.agentx.admin.server.CloudGeminiGateway
import com.agentx.admin.server.LocalModelServerEngine
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class LocalModelServerEngineTest {

    private lateinit var engine: LocalModelServerEngine

    @Before
    fun setUp() {
        engine = LocalModelServerEngine()
    }

    @Test
    fun testCuratedCatalogContainsExpectedEdgeModels() {
        val catalog = engine.curatedCatalog
        assertEquals("Curated catalog should contain 5 mobile edge models", 5, catalog.size)

        val ids = catalog.map { it.id }
        assertTrue(ids.contains("llama-3.2-3b"))
        assertTrue(ids.contains("deepseek-r1-1.5b"))
        assertTrue(ids.contains("qwen-2.5-coder-3b"))
        assertTrue(ids.contains("biomistral-7b"))
        assertTrue(ids.contains("phi-3.5-mini"))
    }

    @Test
    fun testAllModelsHaveBoundedRamFootprint() {
        for (model in engine.curatedCatalog) {
            assertTrue(
                "Model ${model.id} RAM usage (${model.memoryFootprintMb}MB) must be bounded below 250MB",
                model.memoryFootprintMb <= 250L
            )
        }
    }

    @Test
    fun testModelSwitching() = runTest {
        val targetId = "deepseek-r1-1.5b"
        val success = engine.loadModel(targetId)

        assertTrue("Loading $targetId should succeed", success)
        assertEquals("Active model ID should be $targetId", targetId, engine.activeModel.id)
        assertEquals("Status should be LOADED", LocalModelStatus.LOADED, engine.status)
        assertTrue(engine.activeModel.isLoaded)
        assertTrue(engine.activeModel.isServing)
    }

    @Test
    fun testUnloadModel() = runTest {
        engine.unloadModel()
        assertEquals("Status should be NOT_LOADED", LocalModelStatus.NOT_LOADED, engine.status)
        assertFalse(engine.activeModel.isLoaded)
        assertFalse(engine.activeModel.isServing)
    }

    @Test
    fun testSynchronousGenerationSubstantiveOutput() = runTest {
        val output = engine.generateCompletionSync(
            prompt = "Explain layer streaming in AirLLM",
            systemPrompt = "You are a technical AI assistant."
        )

        assertNotNull("Generated output should not be null", output)
        assertTrue("Generated output should not be empty", output.isNotBlank())
        assertTrue(
            "Output should discuss layer streaming or memory efficiency",
            output.contains("Layer-Streaming", ignoreCase = true) || output.contains("AirLLM", ignoreCase = true)
        )
    }

    @Test
    fun testClinicalPromptGeneratesDifferential() = runTest {
        engine.loadModel("biomistral-7b")
        val output = engine.generateCompletionSync(
            prompt = "58yo male with acute retrosternal chest pain and diaphoresis."
        )

        assertTrue(
            "Clinical prompt should generate differential and diagnostics",
            output.contains("Differential", ignoreCase = true) || output.contains("Workup", ignoreCase = true) || output.contains("ECG", ignoreCase = true)
        )
    }

    @Test
    fun testStreamingOutputEmitsProgressiveTokens() = runTest {
        val tokens = engine.generateCompletionStream(
            prompt = "Write a quick test",
            maxTokens = 20
        ).toList()

        assertTrue("Stream should emit multiple tokens", tokens.size > 1)
    }

    @Test
    fun testAdminServerServingModeAndLocalRouting() = runTest {
        val clientManager = ClientManager()
        val cloudGateway = CloudGeminiGateway()
        val server = AgentXAdminServer(clientManager, cloudGateway, engine)

        assertEquals("Default serving mode should be LOCAL_MODEL", ServingMode.LOCAL_MODEL, server.getServingMode())

        server.setServingMode(ServingMode.CLOUD_GATEWAY)
        assertEquals("Serving mode should be CLOUD_GATEWAY", ServingMode.CLOUD_GATEWAY, server.getServingMode())
        assertEquals("Telemetry should reflect CLOUD_GATEWAY", ServingMode.CLOUD_GATEWAY, server.telemetry.value.servingMode)

        server.setServingMode(ServingMode.HYBRID)
        assertEquals("Serving mode should be HYBRID", ServingMode.HYBRID, server.getServingMode())
        assertEquals("Telemetry should reflect HYBRID", ServingMode.HYBRID, server.telemetry.value.servingMode)

        server.setServingMode(ServingMode.LOCAL_MODEL)
        val switched = server.switchLocalModel("qwen-2.5-coder-3b")
        assertTrue(switched)
        assertTrue(server.telemetry.value.activeLocalModel?.contains("Qwen") == true)
    }

    @Test
    fun testRegisterCustomStorageModelAndInference() = runTest {
        val customModel = engine.registerStorageModel(
            name = "mistral-7b-instruct-v0.2.Q4_K_M.gguf",
            filePath = "/storage/emulated/0/Download/mistral-7b-instruct-v0.2.Q4_K_M.gguf",
            fileSizeBytes = 4_300_000_000L
        )

        assertNotNull("Custom storage model should be registered", customModel)
        assertEquals("Architecture should be detected as Mistral", "Mistral", customModel.architecture)
        assertEquals("Parameter count should be detected as 7B", "7B", customModel.parameterCount)
        assertEquals("Quantization should be detected as Q4_K_M", "Q4_K_M", customModel.quantization)
        assertTrue("isCustomStorageModel should be true", customModel.isCustomStorageModel)

        val loaded = engine.loadModel(customModel.id)
        assertTrue("Loading custom storage model should succeed", loaded)
        assertEquals("Active model should be custom model", customModel.id, engine.activeModel.id)

        val output = engine.generateCompletionSync(
            prompt = "Hello from custom model test"
        )
        assertNotNull("Output should not be null", output)
        assertTrue(
            "Output should indicate storage model execution",
            output.contains("Storage Model", ignoreCase = true) || output.contains("Execution completed", ignoreCase = true)
        )

        val removed = engine.removeStorageModel(customModel.id)
        assertTrue("Removing storage model should succeed", removed)
        assertFalse("Storage models list should no longer contain custom model", engine.storageModels.any { it.id == customModel.id })
    }
}
