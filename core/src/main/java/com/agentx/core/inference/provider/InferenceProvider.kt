package com.agentx.core.inference.provider

import com.agentx.core.inference.model.ExecutionMode
import com.agentx.core.inference.model.GenerationEvent
import com.agentx.core.inference.model.GenerationRequest
import com.agentx.core.inference.model.ModelInfo
import com.agentx.core.inference.model.ProviderCapabilities
import com.agentx.core.inference.model.ProviderHealth
import kotlinx.coroutines.flow.Flow

/**
 * Common, decoupling abstraction for all inference providers in the AgentX Suite.
 *
 * Implemented by:
 * - EmbeddedLocalProvider (llama.cpp JNI on-device)
 * - AgentXServeProvider (remote Android device inference hosts via ASP/SSE)
 * - OllamaProvider (desktop & server Ollama instances)
 * - LMStudioProvider (development & desktop hosting)
 * - CloudGatewayProvider (OpenAI-compatible and Google Gemini cloud gateways)
 */
interface InferenceProvider {
    val id: String
    val displayName: String
    val executionMode: ExecutionMode

    suspend fun getModels(): List<ModelInfo>
    suspend fun generate(request: GenerationRequest): Flow<GenerationEvent>
    suspend fun cancel(requestId: String)
    suspend fun healthCheck(): ProviderHealth
    suspend fun capabilities(): ProviderCapabilities
}
