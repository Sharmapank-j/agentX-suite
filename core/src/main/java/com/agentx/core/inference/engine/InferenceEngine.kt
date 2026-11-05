package com.agentx.core.inference.engine

import com.agentx.core.inference.model.GenerationEvent
import com.agentx.core.inference.model.GenerationRequest
import com.agentx.core.inference.model.ModelInfo
import kotlinx.coroutines.flow.Flow

/**
 * Clean InferenceEngine interface specified in Section B and Phase 2 of Master Architecture.
 */
interface InferenceEngine {
    val engineName: String
    val isModelLoaded: Boolean
    val loadedModel: ModelInfo?

    suspend fun loadModel(model: ModelInfo): Result<Unit>
    suspend fun generate(request: GenerationRequest): Flow<GenerationEvent>
    suspend fun cancel(requestId: String)
    suspend fun unloadModel(): Result<Unit>
}
