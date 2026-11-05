package com.agentx.core

import com.agentx.core.inference.engine.InferenceEngine
import com.agentx.core.inference.model.GenerationEvent
import com.agentx.core.inference.model.GenerationRequest
import com.agentx.core.inference.model.ModelInfo
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Test-only mock engine strictly restricted to unit tests.
 * Never used in production execution paths.
 */
class FakeInferenceEngine : InferenceEngine {
    override val engineName: String = "Fake_Inference_Engine_Test_Only"

    private var _loadedModel: ModelInfo? = null
    override val loadedModel: ModelInfo? get() = _loadedModel
    override val isModelLoaded: Boolean get() = _loadedModel != null

    override suspend fun loadModel(model: ModelInfo): Result<Unit> {
        _loadedModel = model
        return Result.success(Unit)
    }

    override suspend fun generate(request: GenerationRequest): Flow<GenerationEvent> = flow {
        emit(GenerationEvent.Token("Hello", 0))
        emit(GenerationEvent.Token(" world", 1))
        emit(
            GenerationEvent.Finished(
                totalTokens = 2,
                promptTokens = 1,
                completionTokens = 2,
                durationMs = 5L,
                providerId = "test_fake",
                modelId = _loadedModel?.id ?: "fake_model"
            )
        )
    }

    override suspend fun cancel(requestId: String) {}

    override suspend fun unloadModel(): Result<Unit> {
        _loadedModel = null
        return Result.success(Unit)
    }
}
