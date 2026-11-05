package com.agentx.core

import com.agentx.core.inference.model.GenerationEvent
import com.agentx.core.inference.model.GenerationRequest
import com.agentx.core.inference.model.ModelInfo
import com.agentx.core.inference.model.ProviderCapabilities
import com.agentx.core.inference.model.ProviderHealth
import com.agentx.core.inference.provider.LLMProvider
import com.agentx.core.inference.scheduler.DefaultInferenceScheduler
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class InferenceSchedulerTest {

    private class MockLlmProvider(override val id: String = "mock_provider") : LLMProvider {
        override val displayName: String = "Mock Provider"
        override val executionMode = com.agentx.core.inference.model.ExecutionMode.LOCAL

        var activeModelId: String = "model_1"

        override suspend fun getModels(): List<ModelInfo> = emptyList()
        override suspend fun getSelectedModel(): ModelInfo? = null
        override suspend fun selectModel(modelId: String): Result<Unit> {
            activeModelId = modelId
            return Result.success(Unit)
        }
        override suspend fun generate(request: GenerationRequest): Flow<GenerationEvent> = flow {
            emit(GenerationEvent.Token("token1"))
            emit(GenerationEvent.Token("token2"))
            emit(GenerationEvent.Finished(2, 1, 1, 10L, id, activeModelId))
        }
        override suspend fun cancel(requestId: String) {}
        override suspend fun healthCheck(): ProviderHealth = ProviderHealth(isHealthy = true)
        override suspend fun capabilities(): ProviderCapabilities = ProviderCapabilities()
    }

    @Test
    fun testMemoryBudgetRejection() = runBlocking {
        val scheduler = DefaultInferenceScheduler(memoryBudgetMb = 2048L)
        val oversizedModel = ModelInfo(
            id = "oversized-70b",
            name = "Llama 70B",
            memoryFootprintMb = 40960L,
            isLocal = true
        )

        val result = scheduler.loadModel(oversizedModel)
        assertTrue("Model exceeding budget must be rejected", result.isFailure)
        assertFalse(scheduler.isModelLoaded.value)
        assertEquals(0L, scheduler.currentMemoryUsageMb.value)
    }

    @Test
    fun testSingleActiveModelInvariant() = runBlocking {
        val scheduler = DefaultInferenceScheduler(memoryBudgetMb = 2048L)
        val modelA = ModelInfo(id = "model-3b", name = "Model 3B", memoryFootprintMb = 1200L, isLocal = true)
        val modelB = ModelInfo(id = "model-1.5b", name = "Model 1.5B", memoryFootprintMb = 800L, isLocal = true)

        // Load Model A
        val resA = scheduler.loadModel(modelA)
        assertTrue(resA.isSuccess)
        assertEquals(modelA.id, scheduler.activeModel.value?.id)
        assertEquals(1200L, scheduler.currentMemoryUsageMb.value)

        // Load Model B -> Should replace Model A in the single active slot
        val resB = scheduler.loadModel(modelB)
        assertTrue(resB.isSuccess)
        assertEquals(modelB.id, scheduler.activeModel.value?.id)
        assertEquals(800L, scheduler.currentMemoryUsageMb.value)
    }

    @Test
    fun testUnloadAndOomRecovery() = runBlocking {
        val scheduler = DefaultInferenceScheduler(memoryBudgetMb = 2048L)
        val model = ModelInfo(id = "model-3b", name = "Model 3B", memoryFootprintMb = 1200L, isLocal = true)

        scheduler.loadModel(model)
        assertTrue(scheduler.isModelLoaded.value)

        scheduler.handleOomRecovery()
        assertFalse(scheduler.isModelLoaded.value)
        assertNull(scheduler.activeModel.value)
        assertEquals(0L, scheduler.currentMemoryUsageMb.value)
    }

    @Test
    fun testScheduleGenerationExecution() = runBlocking {
        val scheduler = DefaultInferenceScheduler(memoryBudgetMb = 2048L)
        val provider = MockLlmProvider()
        val request = GenerationRequest(prompt = "Hello scheduler")

        val events = scheduler.scheduleGeneration(request, provider).toList()
        assertEquals(3, events.size)
        assertTrue(events[0] is GenerationEvent.Token)
        assertTrue(events[2] is GenerationEvent.Finished)
        assertEquals(0, scheduler.activeRequestCount.value)
    }
}
