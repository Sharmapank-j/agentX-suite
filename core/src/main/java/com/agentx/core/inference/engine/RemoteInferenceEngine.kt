package com.agentx.core.inference.engine

import com.agentx.core.inference.model.GenerationEvent
import com.agentx.core.inference.model.GenerationRequest
import com.agentx.core.inference.model.ModelInfo
import com.agentx.core.inference.provider.LLMProvider
import kotlinx.coroutines.flow.Flow

/**
 * Remote Inference Engine delegating to any remote OpenAI-compatible LLMProvider.
 */
class RemoteInferenceEngine(
    private val provider: LLMProvider
) : InferenceEngine {

    override val engineName: String = "Remote_Inference_Engine (${provider.displayName})"

    private var _loadedModel: ModelInfo? = null
    override val loadedModel: ModelInfo? get() = _loadedModel
    override val isModelLoaded: Boolean get() = _loadedModel != null

    override suspend fun loadModel(model: ModelInfo): Result<Unit> {
        val res = provider.selectModel(model.id)
        if (res.isSuccess) {
            _loadedModel = model
        }
        return res
    }

    override suspend fun generate(request: GenerationRequest): Flow<GenerationEvent> {
        return provider.generate(request)
    }

    override suspend fun cancel(requestId: String) {
        provider.cancel(requestId)
    }

    override suspend fun unloadModel(): Result<Unit> {
        _loadedModel = null
        return Result.success(Unit)
    }
}
