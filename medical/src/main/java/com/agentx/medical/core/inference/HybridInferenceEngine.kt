package com.agentx.medical.core.inference

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow

enum class LocalBackendType {
    @Deprecated("Legacy alias, maps to LLAMA_CPP")
    AIR_LLM,
    LLAMA_CPP     // In-memory mapped GGUF engine via native llama.cpp
}

class HybridInferenceEngine(
    val llamaEngine: InferenceEngine = LlamaCppEngine(),
    val cloudEngine: CloudInferenceEngine = CloudInferenceEngine(),
    var activeLocalBackend: LocalBackendType = LocalBackendType.LLAMA_CPP
) : InferenceEngine {

    // Overload constructor for backwards compatibility with existing test suites
    constructor(
        localEngine: InferenceEngine,
        cloudEngine: CloudInferenceEngine = CloudInferenceEngine()
    ) : this(
        llamaEngine = localEngine,
        cloudEngine = cloudEngine,
        activeLocalBackend = LocalBackendType.LLAMA_CPP
    )

    var inferenceMode: InferenceMode = InferenceMode.LOCAL_ONLY

    val localEngine: InferenceEngine
        get() = llamaEngine

    override suspend fun loadModel(config: ModelConfig) {
        localEngine.loadModel(config)
    }

    override fun generate(request: InferenceRequest): Flow<GenerationEvent> {
        return when (inferenceMode) {
            InferenceMode.LOCAL_ONLY -> localEngine.generate(request)
            InferenceMode.CLOUD_ONLY -> cloudEngine.generate(request)
            InferenceMode.HYBRID_AUTO -> channelFlow {
                val hasCloudKey = cloudEngine.config.provider == CloudProvider.CUSTOM_GATEWAY ||
                        cloudEngine.config.apiKeyFor(CloudProvider.GEMINI).isNotBlank()

                if (hasCloudKey) {
                    var hasEmittedCloudToken = false
                    var cloudFailed = false

                    try {
                        cloudEngine.generate(request).collect { event ->
                            when (event) {
                                is GenerationEvent.Token -> {
                                    hasEmittedCloudToken = true
                                    send(event)
                                }
                                is GenerationEvent.Finished -> {
                                    send(event)
                                }
                                is GenerationEvent.Error -> {
                                    if (!hasEmittedCloudToken) {
                                        cloudFailed = true
                                    } else {
                                        send(event)
                                    }
                                }
                            }
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        if (!hasEmittedCloudToken) {
                            cloudFailed = true
                        } else {
                            send(GenerationEvent.Error(e, e.message ?: "Cloud generation failed"))
                        }
                    }

                    if (cloudFailed) {
                        localEngine.generate(request).collect { send(it) }
                    }
                } else {
                    localEngine.generate(request).collect { send(it) }
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
