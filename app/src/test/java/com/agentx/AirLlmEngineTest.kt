package com.agentx

import com.agentx.core.inference.AirLlmEngine
import com.agentx.core.inference.GenerationEvent
import com.agentx.core.inference.InferenceRequest
import com.agentx.core.inference.ModelConfig
import com.agentx.core.inference.ModelLifecycleState
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class AirLlmEngineTest {

    private lateinit var engine: AirLlmEngine

    @Before
    fun setUp() {
        engine = AirLlmEngine()
    }

    @Test
    fun testInitialStatusNotLoaded() {
        val status = engine.getModelStatus()
        assertEquals(ModelLifecycleState.NOT_LOADED, status.state)
        assertEquals(0L, status.memoryUsageBytes)
    }

    @Test
    fun testLoadModelBoundsMemoryFootprint() = runTest {
        val config = ModelConfig(
            modelId = "airllm_deepseek_7b",
            modelPath = "/models/deepseek-7b-q4.gguf",
            contextLength = 4096
        )
        engine.loadModel(config)

        val status = engine.getModelStatus()
        assertEquals(ModelLifecycleState.LOADED, status.state)
        assertEquals("airllm_deepseek_7b", status.activeModelId)
        assertTrue("Memory footprint must be bounded under memory budget (>1.5GB)", status.memoryUsageBytes <= engine.maxMemoryBudgetBytes)
        assertTrue("Active memory must be > 0", status.memoryUsageBytes > 0)
    }

    @Test
    fun testGenerationProducesSubstantiveMedicalOutput() = runTest {
        val request = InferenceRequest(
            prompt = "Explain the phases of the cardiac cycle and hemodynamics in detail",
            maxTokens = 200
        )
        val events = engine.generate(request).toList()
        assertTrue("Expected tokens emitted", events.any { it is GenerationEvent.Token })

        val tokens = events.filterIsInstance<GenerationEvent.Token>().joinToString("") { it.text }
        assertTrue("Substantive cardiac physiology expected", tokens.lowercase().contains("cardiac") && tokens.lowercase().contains("systole"))
    }

    @Test
    fun testGenerationProducesSubstantiveCodeOutput() = runTest {
        val request = InferenceRequest(
            prompt = "Write a Kotlin StateFlow and Coroutines debounce flow pattern",
            maxTokens = 200
        )
        val events = engine.generate(request).toList()
        val tokens = events.filterIsInstance<GenerationEvent.Token>().joinToString("") { it.text }
        assertTrue("Substantive Kotlin code expected", tokens.contains("Kotlin") && (tokens.contains("Coroutine") || tokens.contains("Flow") || tokens.contains("StateFlow")))
    }

    @Test
    fun testUnloadModelFreesMemory() = runTest {
        val config = ModelConfig(modelId = "test_model", modelPath = "path/test.gguf")
        engine.loadModel(config)
        assertEquals(ModelLifecycleState.LOADED, engine.getModelStatus().state)

        engine.unloadModel()
        val status = engine.getModelStatus()
        assertEquals(ModelLifecycleState.NOT_LOADED, status.state)
        assertEquals(0L, status.memoryUsageBytes)
    }
}
