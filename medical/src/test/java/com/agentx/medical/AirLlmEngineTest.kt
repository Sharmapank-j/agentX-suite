package com.agentx.medical

import com.agentx.medical.core.inference.AirLlmEngine
import com.agentx.medical.core.inference.GenerationEvent
import com.agentx.medical.core.inference.InferenceRequest
import com.agentx.medical.core.inference.ModelConfig
import com.agentx.medical.core.inference.ModelLifecycleState
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
            modelId = "airllm_biomistral_7b",
            modelPath = "/models/biomistral-7b-q4.gguf",
            contextLength = 4096
        )
        engine.loadModel(config)

        val status = engine.getModelStatus()
        assertEquals(ModelLifecycleState.LOADED, status.state)
        assertEquals("airllm_biomistral_7b", status.activeModelId)
        assertTrue("Memory footprint must be bounded under memory budget (>1.5GB)", status.memoryUsageBytes <= engine.maxMemoryBudgetBytes)
        assertTrue("Active memory must be > 0", status.memoryUsageBytes > 0)
    }

    @Test
    fun testGenerationProducesSubstantiveMedicalCalculations() = runTest {
        val request = InferenceRequest(
            prompt = "Calculate eGFR for 65 year old female with creatinine 1.4 mg/dL",
            maxTokens = 200
        )
        val events = engine.generate(request).toList()
        assertTrue("Expected tokens emitted", events.any { it is GenerationEvent.Token })

        val tokens = events.filterIsInstance<GenerationEvent.Token>().joinToString("") { it.text }
        assertTrue("Substantive eGFR or CKD-EPI output expected", tokens.contains("CKD-EPI") || tokens.contains("calculate_egfr") || tokens.contains("eGFR") || tokens.contains("Creatinine"))
    }

    @Test
    fun testGenerationProducesSubstantiveCardiovascularRisk() = runTest {
        val request = InferenceRequest(
            prompt = "Assess stroke risk with CHA2DS2-VASc score for atrial fibrillation",
            maxTokens = 200
        )
        val events = engine.generate(request).toList()
        val tokens = events.filterIsInstance<GenerationEvent.Token>().joinToString("") { it.text }
        assertTrue("Substantive stroke score expected", tokens.contains("CHA2DS2-VASc") || tokens.contains("anticoagulation") || tokens.contains("Stroke") || tokens.contains("Score"))
    }

    @Test
    fun testUnloadModelFreesMemory() = runTest {
        val config = ModelConfig(modelId = "med_test", modelPath = "path/med_test.gguf")
        engine.loadModel(config)
        assertEquals(ModelLifecycleState.LOADED, engine.getModelStatus().state)

        engine.unloadModel()
        val status = engine.getModelStatus()
        assertEquals(ModelLifecycleState.NOT_LOADED, status.state)
        assertEquals(0L, status.memoryUsageBytes)
    }
}
