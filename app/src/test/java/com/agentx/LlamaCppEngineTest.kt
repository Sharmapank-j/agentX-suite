package com.agentx

import com.agentx.core.inference.GenerationEvent
import com.agentx.core.inference.InferenceRequest
import com.agentx.core.inference.LlamaCppEngine
import com.agentx.core.inference.ModelConfig
import com.agentx.core.inference.ModelLifecycleState
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class LlamaCppEngineTest {
    @Test fun startsNotLoaded() {
        assertEquals(ModelLifecycleState.NOT_LOADED, LlamaCppEngine().getModelStatus().state)
    }

    @Test fun refusesMissingOrUnavailableNativeModel() = runTest {
        val engine = LlamaCppEngine()
        try {
            engine.loadModel(ModelConfig(modelId = "fixture", modelPath = "/missing/model.gguf"))
            fail("A stub runtime must not claim a model was loaded")
        } catch (_: IllegalStateException) {
            // Host JVM has no Android native library; unavailable runtime must fail closed.
        } catch (_: IllegalArgumentException) {
            // Missing model path is rejected before native loading on some platforms.
        }
        assertNotEquals(ModelLifecycleState.LOADED, engine.getModelStatus().state)
    }

    @Test fun emitsExplicitErrorAndNoSyntheticTokens() = runTest {
        val events = LlamaCppEngine().generate(InferenceRequest(prompt = "test")).toList()
        assertTrue(events.any { it is GenerationEvent.Error })
        assertFalse(events.any { it is GenerationEvent.Token })
        assertFalse(events.any { it is GenerationEvent.Finished })
    }
}
