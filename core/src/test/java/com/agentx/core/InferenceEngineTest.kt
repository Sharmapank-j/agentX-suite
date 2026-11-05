package com.agentx.core

import com.agentx.core.inference.engine.RemoteInferenceEngine
import com.agentx.core.inference.model.GenerationEvent
import com.agentx.core.inference.model.GenerationRequest
import com.agentx.core.inference.model.ModelInfo
import com.agentx.core.inference.provider.OpenAiCompatibleProvider
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InferenceEngineTest {

    @Test
    fun testFakeInferenceEngineLifecycleAndGeneration() = runBlocking {
        val engine = FakeInferenceEngine()
        assertFalse(engine.isModelLoaded)

        val model = ModelInfo(id = "test-model-1", name = "Test Model")
        val loadRes = engine.loadModel(model)
        assertTrue(loadRes.isSuccess)
        assertTrue(engine.isModelLoaded)
        assertEquals(model.id, engine.loadedModel?.id)

        val request = GenerationRequest(prompt = "Test prompt")
        val events = engine.generate(request).toList()
        assertEquals(3, events.size)
        assertTrue(events[0] is GenerationEvent.Token)
        assertTrue(events[2] is GenerationEvent.Finished)

        val unloadRes = engine.unloadModel()
        assertTrue(unloadRes.isSuccess)
        assertFalse(engine.isModelLoaded)
    }

    @Test
    fun testRemoteInferenceEngineDelegation() = runBlocking {
        val provider = OpenAiCompatibleProvider(baseUrl = "http://127.0.0.1:8080")
        val engine = RemoteInferenceEngine(provider)

        val model = ModelInfo(id = "remote-model-3b", name = "Remote Model", isLocal = false)
        val loadRes = engine.loadModel(model)
        assertTrue(loadRes.isSuccess)
        assertTrue(engine.isModelLoaded)
        assertEquals(model.id, engine.loadedModel?.id)

        val unloadRes = engine.unloadModel()
        assertTrue(unloadRes.isSuccess)
        assertFalse(engine.isModelLoaded)
    }
}
