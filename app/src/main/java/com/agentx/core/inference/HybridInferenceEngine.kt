package com.agentx.core.inference

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList

class HybridInferenceEngine(
    val localEngine: InferenceEngine,
    val cloudEngine: CloudInferenceEngine = CloudInferenceEngine()
) : InferenceEngine {

    var inferenceMode: InferenceMode = InferenceMode.HYBRID_AUTO

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
                        // Seamlessly fallback to on-device GGUF
                        emit(GenerationEvent.Token("[Cloud unavailable — auto-switching to On-Device GGUF Engine]\n\n"))
                        localEngine.generate(request).collect { emit(it) }
                    }
                } else {
                    // No cloud key configured, run locally
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
