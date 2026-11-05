package com.agentx.core.inference.provider

import com.agentx.core.inference.model.ExecutionMode
import com.agentx.core.inference.model.GenerationEvent
import com.agentx.core.inference.model.GenerationRequest
import com.agentx.core.inference.model.ModelInfo
import com.agentx.core.inference.model.ProviderCapabilities
import com.agentx.core.inference.model.ProviderHealth
import kotlinx.coroutines.flow.Flow

/**
 * Unified LLMProvider abstraction specified in Section B of the Master Architecture.
 *
 * Exposes core capabilities common to all local and remote inference backends:
 * - Model discovery & selection
 * - Health status & error reporting
 * - Incremental generation & SSE streaming
 * - Cooperative cancellation
 * - Detailed capability inspection
 */
interface LLMProvider : InferenceProvider {
    /** Currently active/selected model on this provider */
    suspend fun getSelectedModel(): ModelInfo?

    /** Switch the active model identifier */
    suspend fun selectModel(modelId: String): Result<Unit>

    /** Convenience helper for token-by-token streaming */
    suspend fun stream(request: GenerationRequest): Flow<GenerationEvent> = generate(request)

    /** Query provider health and connectivity */
    suspend fun healthStatus(): ProviderHealth = healthCheck()

    /** Detailed capability descriptor */
    suspend fun getCapabilities(): ProviderCapabilities = capabilities()
}

/**
 * Optional capability interface for vision-language models (VLM).
 */
interface VisionCapability {
    suspend fun generateWithVision(
        imageBytes: ByteArray,
        mimeType: String,
        prompt: String,
        systemPrompt: String? = null
    ): Flow<GenerationEvent>
}

/**
 * Optional capability interface for dense vector embedding generation.
 */
interface EmbeddingsCapability {
    suspend fun generateEmbeddings(
        texts: List<String>,
        modelId: String? = null
    ): List<FloatArray>
}

/**
 * Optional capability interface for cross-encoder reranking of retrieved documents.
 */
interface RerankingCapability {
    suspend fun rerank(
        query: String,
        documents: List<String>,
        topK: Int = 5
    ): List<RerankResult>
}

data class RerankResult(
    val index: Int,
    val score: Float,
    val document: String
)

/**
 * Optional capability interface for native structured tool calling.
 */
interface ToolCallingCapability {
    suspend fun generateWithTools(
        request: GenerationRequest,
        toolSchemasJson: String
    ): Flow<GenerationEvent>
}
