package com.agentx

import com.agentx.core.inference.GenerationEvent
import com.agentx.core.inference.InferenceRequest
import com.agentx.core.inference.LlamaCppEngine
import com.agentx.core.inference.ModelConfig
import com.agentx.core.inference.ModelLifecycleState
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class LlamaCppEngineTest {

    private lateinit var engine: LlamaCppEngine

    @Before
    fun setUp() {
        engine = LlamaCppEngine()
    }

    @Test
    fun testInitialStatusNotLoaded() {
        val status = engine.getModelStatus()
        assertEquals(ModelLifecycleState.NOT_LOADED, status.state)
        assertNull(status.activeModelId)
        assertEquals(0L, status.memoryUsageBytes)
    }

    @Test
    fun testLoadModelTransitionsToLoaded() = runTest {
        val config = ModelConfig(
            modelId = "model_biomistral_7b",
            modelPath = "/data/user/0/com.agentx/files/models/biomistral-7b-q4_k_m.gguf",
            contextLength = 4096,
            threads = 4,
            gpuLayers = 0
        )

        engine.loadModel(config)
        val status = engine.getModelStatus()

        assertEquals(ModelLifecycleState.LOADED, status.state)
        assertEquals("model_biomistral_7b", status.activeModelId)
        assertEquals("biomistral-7b-q4_k_m.gguf", status.activeModelName)
        assertTrue(status.memoryUsageBytes > 0)
    }

    @Test
    fun testGenerationStreamEmitsTokensAndFinished() = runTest {
        val config = ModelConfig(
            modelId = "model_biomistral_7b",
            modelPath = "/data/user/0/com.agentx/files/models/biomistral-7b-q4_k_m.gguf"
        )
        engine.loadModel(config)

        val request = InferenceRequest(
            prompt = "What are the first-line therapies for essential hypertension?",
            systemPrompt = "You are a clinical decision support specialist.",
            maxTokens = 20,
            temperature = 0.2f
        )

        val events = engine.generate(request).toList()
        assertTrue("Expected at least one token event", events.any { it is GenerationEvent.Token })

        val finishedEvent = events.filterIsInstance<GenerationEvent.Finished>().firstOrNull()
        assertNotNull("Expected Finished event at the end of generation", finishedEvent)
        assertTrue((finishedEvent?.totalTokens ?: 0) > 0)

        val reconstructedText = events.filterIsInstance<GenerationEvent.Token>()
            .joinToString("") { it.text }
        assertTrue(reconstructedText.isNotBlank())
    }

    @Test
    fun testGenerationWithoutModelEmitsError() = runTest {
        val request = InferenceRequest(prompt = "Test prompt")
        val events = engine.generate(request).toList()

        assertEquals(1, events.size)
        assertTrue("Expected error event when no model is loaded", events.first() is GenerationEvent.Error)
        val errorEvent = events.first() as GenerationEvent.Error
        assertTrue(errorEvent.message.contains("No model"))
    }

    @Test
    fun testUnloadModelTransitionsToNotLoaded() = runTest {
        val config = ModelConfig(
            modelId = "model_meditron_7b",
            modelPath = "/data/user/0/com.agentx/files/models/meditron-7b-q4_k_m.gguf"
        )
        engine.loadModel(config)
        assertEquals(ModelLifecycleState.LOADED, engine.getModelStatus().state)

        engine.unloadModel()
        val postUnloadStatus = engine.getModelStatus()
        assertEquals(ModelLifecycleState.NOT_LOADED, postUnloadStatus.state)
        assertNull(postUnloadStatus.activeModelId)
    }
}
