package com.agentx.medical.core.inference

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

enum class LocalBackendType {
    AIR_LLM,      // Ultra-lightweight layer-streaming block inference (<250 MB RAM, best for mobile)
    LLAMA_CPP     // In-memory mapped GGUF engine
}

class HybridInferenceEngine(
    val llamaEngine: InferenceEngine = LlamaCppEngine(),
    val airLlmEngine: InferenceEngine = AirLlmEngine(),
    val cloudEngine: CloudInferenceEngine = CloudInferenceEngine(),
    var activeLocalBackend: LocalBackendType = LocalBackendType.AIR_LLM
) : InferenceEngine {

    constructor(
        localEngine: InferenceEngine,
        cloudEngine: CloudInferenceEngine = CloudInferenceEngine()
    ) : this(
        llamaEngine = localEngine,
        airLlmEngine = AirLlmEngine(),
        cloudEngine = cloudEngine,
        activeLocalBackend = LocalBackendType.LLAMA_CPP
    )

    var inferenceMode: InferenceMode = InferenceMode.HYBRID_AUTO

    val localEngine: InferenceEngine
        get() = when (activeLocalBackend) {
            LocalBackendType.AIR_LLM -> airLlmEngine
            LocalBackendType.LLAMA_CPP -> llamaEngine
        }

    override suspend fun loadModel(config: ModelConfig) {
        localEngine.loadModel(config)
    }

    override fun generate(request: InferenceRequest): Flow<GenerationEvent> = flow {
        when (inferenceMode) {
            InferenceMode.LOCAL_ONLY -> {
                localEngine.generate(request).collect { emit(it) }
            }
            InferenceMode.CLOUD_ONLY -> {
                cloudEngine.generate(request).collect { emit(it) }
            }
            InferenceMode.HYBRID_AUTO -> {
                val hasCloudKey = cloudEngine.config.apiKey.isNotBlank() ||
                        cloudEngine.config.provider == CloudProvider.CUSTOM_GATEWAY

                if (hasCloudKey) {
                    var hasEmittedCloudToken = false
                    var cloudFailed = false

                    try {
                        cloudEngine.generate(request).collect { event ->
                            when (event) {
                                is GenerationEvent.Token -> {
                                    hasEmittedCloudToken = true
                                    emit(event)
                                }
                                is GenerationEvent.Finished -> {
                                    emit(event)
                                }
                                is GenerationEvent.Error -> {
                                    if (!hasEmittedCloudToken) {
                                        cloudFailed = true
                                    } else {
                                        emit(event)
                                    }
                                }
                            }
                        }
                    } catch (e: Exception) {
                        if (!hasEmittedCloudToken) {
                            cloudFailed = true
                        } else {
                            emit(GenerationEvent.Error(e, e.message ?: "Cloud generation failed"))
                        }
                    }

                    if (cloudFailed) {
                        val backendName = if (activeLocalBackend == LocalBackendType.AIR_LLM) "AirLLM Layer-Streaming" else "LlamaCpp GGUF"
                        emit(GenerationEvent.Token("[Cloud unavailable — auto-switching to On-Device $backendName Engine]\n\n"))
                        localEngine.generate(request).collect { emit(it) }
                    }
                } else {
                    localEngine.generate(request).collect { emit(it) }
                }
            }
        }
    }

    override suspend fun stopGeneration() {
        cloudEngine.stopGeneration()
        localEngine.stopGeneration()
    }

    override suspend fun unloadModel() {
        cloudEngine.unloadModel()
        localEngine.unloadModel()
    }

    override fun getModelStatus(): ModelStatus {
        return when (inferenceMode) {
            InferenceMode.LOCAL_ONLY -> localEngine.getModelStatus()
            InferenceMode.CLOUD_ONLY -> cloudEngine.getModelStatus()
            InferenceMode.HYBRID_AUTO -> {
                val cloudStat = cloudEngine.getModelStatus()
                if (cloudStat.state == ModelLifecycleState.GENERATING) cloudStat
                else localEngine.getModelStatus()
            }
        }
    }
}
