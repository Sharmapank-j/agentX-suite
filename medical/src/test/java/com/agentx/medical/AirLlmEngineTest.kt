package com.agentx.medical

import com.agentx.medical.core.inference.AirLlmEngine
import com.agentx.medical.core.inference.GenerationEvent
import com.agentx.medical.core.inference.InferenceRequest
import com.agentx.medical.core.inference.ModelConfig
import com.agentx.medical.core.inference.ModelLifecycleState
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class AirLlmEngineTest {
    @Test fun startsNotLoaded() {
        assertEquals(ModelLifecycleState.NOT_LOADED, AirLlmEngine().getModelStatus().state)
    }

    @Test fun refusesToClaimModelLoadedWithoutRuntime() = runTest {
        val engine = AirLlmEngine()
        try {
            engine.loadModel(ModelConfig(modelId = "fixture", modelPath = "/missing/model.gguf"))
            fail("A simulated medical engine must not claim a model was loaded")
        } catch (expected: UnsupportedOperationException) {
            assertTrue(expected.message.orEmpty().contains("Real GGUF inference"))
        }
        assertEquals(ModelLifecycleState.NOT_LOADED, engine.getModelStatus().state)
    }

    @Test fun emitsErrorWithoutSyntheticClinicalOutput() = runTest {
        val events = AirLlmEngine().generate(InferenceRequest(prompt = "Calculate eGFR")).toList()
        assertTrue(events.any { it is GenerationEvent.Error })
        assertFalse(events.any { it is GenerationEvent.Token })
        assertFalse(events.any { it is GenerationEvent.Finished })
    }
}
