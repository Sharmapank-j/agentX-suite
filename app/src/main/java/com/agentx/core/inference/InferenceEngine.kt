package com.agentx.core.inference

import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable

@Serializable
data class ModelConfig(
    val modelId: String,
    val modelPath: String,
    val contextLength: Int = 2048,
    val threads: Int = 4,
    val gpuLayers: Int = 0,
    val batchSize: Int = 512,
    val temperature: Float = 0.7f,
    val topP: Float = 0.9f,
    val repeatPenalty: Float = 1.1f
)

@Serializable
data class InferenceRequest(
    val prompt: String,
    val systemPrompt: String? = null,
    val maxTokens: Int = 1024,
    val temperature: Float = 0.7f,
    val topP: Float = 0.9f,
    val stopSequences: List<String> = emptyList()
)

sealed interface GenerationEvent {
    data class Token(val text: String) : GenerationEvent
    data class Finished(
        val totalTokens: Int,
        val promptTokens: Int,
        val completionTokens: Int,
        val durationMs: Long
    ) : GenerationEvent
    data class Error(val throwable: Throwable, val message: String) : GenerationEvent
}

@Serializable
enum class ModelLifecycleState {
    NOT_LOADED,
    LOADING,
    LOADED,
    GENERATING,
    ERROR
}

@Serializable
data class ModelStatus(
    val state: ModelLifecycleState,
    val activeModelId: String? = null,
    val activeModelName: String? = null,
    val memoryUsageBytes: Long = 0L,
    val errorMessage: String? = null
)

interface InferenceEngine {
    suspend fun loadModel(config: ModelConfig)
    fun generate(request: InferenceRequest): Flow<GenerationEvent>
    suspend fun stopGeneration()
    suspend fun unloadModel()
    fun getModelStatus(): ModelStatus
}
