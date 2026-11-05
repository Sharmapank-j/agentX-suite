package com.agentx.core.inference

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Fail-closed placeholder retained for source compatibility.
 *
 * This class does not execute GGUF tensors. It deliberately refuses to report a
 * model as loaded or emit fabricated tokens. Replace it with the shared native
 * llama.cpp runtime before enabling local inference in this application.
 */
class AirLlmEngine(val maxMemoryBudgetBytes: Long = 2L * 1024L * 1024L * 1024L) : InferenceEngine {
    private val mutex = Mutex()
    private val cancelled = AtomicBoolean(false)

    @Volatile
    private var status = ModelStatus(
        state = ModelLifecycleState.NOT_LOADED,
        memoryUsageBytes = 0L
    )

    override suspend fun loadModel(config: ModelConfig) {
        mutex.withLock {
            status = ModelStatus(
                state = ModelLifecycleState.NOT_LOADED,
                activeModelId = null,
                activeModelName = null,
                memoryUsageBytes = 0L
            )
        }
        throw UnsupportedOperationException(
            "Real GGUF inference is not connected. No model was loaded; use a configured native llama.cpp runtime."
        )
    }

    override fun generate(request: InferenceRequest): Flow<GenerationEvent> = flow {
        cancelled.set(false)
        val error = UnsupportedOperationException(
            "Local generation is disabled because this engine does not execute GGUF model weights. No synthetic response was generated."
        )
        emit(GenerationEvent.Error(error, error.message ?: "Native inference unavailable"))
    }

    override suspend fun stopGeneration() {
        cancelled.set(true)
    }

    override suspend fun unloadModel() {
        mutex.withLock {
            cancelled.set(true)
            status = ModelStatus(
                state = ModelLifecycleState.NOT_LOADED,
                memoryUsageBytes = 0L
            )
        }
    }

    override fun getModelStatus(): ModelStatus = status
}
