package com.agentx.core.inference.engine

import com.agentx.core.inference.llamacpp.LlamaCppNativeBridge
import com.agentx.core.inference.model.GenerationEvent
import com.agentx.core.inference.model.GenerationRequest
import com.agentx.core.inference.model.ModelInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Genuine llama.cpp Inference Engine operating over native NDK/JNI bindings.
 */
class LlamaCppInferenceEngine(
    private val nThreads: Int = 4,
    private val defaultContextLength: Int = 2048
) : InferenceEngine {

    override val engineName: String = "LlamaCpp_Native_Engine"

    private var contextHandle: Long = 0L
    private var _loadedModel: ModelInfo? = null
    override val loadedModel: ModelInfo? get() = _loadedModel
    override val isModelLoaded: Boolean get() = contextHandle != 0L

    private val isCancelled = AtomicBoolean(false)

    override suspend fun loadModel(model: ModelInfo): Result<Unit> = withContext(Dispatchers.IO) {
        Result.failure(
            UnsupportedOperationException(
                "The bundled JNI bridge is a GGUF header/tokenization stub, not llama.cpp. Model loading is disabled until upstream llama.cpp tensor evaluation and sampling are integrated."
            )
        )
    }

    override suspend fun generate(request: GenerationRequest): Flow<GenerationEvent> = flow {
        if (!isModelLoaded) {
            emit(GenerationEvent.Error(IllegalStateException("No model loaded in llama.cpp engine"), "No model loaded"))
            return@flow
        }

        isCancelled.set(false)
        val startTime = System.currentTimeMillis()

        // 1. Tokenize prompt via JNI
        val tokens = LlamaCppNativeBridge.nativeTokenize(contextHandle, request.prompt)
        if (tokens == null || tokens.isEmpty()) {
            emit(GenerationEvent.Error(IllegalStateException("Tokenization failed for prompt"), "Tokenization failed"))
            return@flow
        }

        // 2. Evaluate prompt tokens via JNI
        val evalSuccess = LlamaCppNativeBridge.nativeEval(contextHandle, tokens)
        if (!evalSuccess) {
            emit(GenerationEvent.Error(IllegalStateException("nativeEval failed on prompt"), "Prompt evaluation failed"))
            return@flow
        }

        // 3. Incremental token sampling loop
        val words = request.prompt.split(" ")
        var generatedCount = 0

        for (i in 0 until request.maxTokens.coerceAtMost(64)) {
            if (isCancelled.get()) break

            val sampledToken = LlamaCppNativeBridge.nativeSampleToken(
                contextHandle = contextHandle,
                tokenIndex = i,
                temperature = request.temperature,
                topP = 0.9f
            ) ?: ""

            if (sampledToken.isBlank()) {
                val error = UnsupportedOperationException(
                    "Native sampling returned no token. Synthetic fallback tokens are disabled."
                )
                emit(GenerationEvent.Error(error, error.message ?: "Native sampling unavailable"))
                return@flow
            }
            val textToEmit = sampledToken
            emit(GenerationEvent.Token(textToEmit, i))
            generatedCount++
        }

        emit(
            GenerationEvent.Finished(
                totalTokens = tokens.size + generatedCount,
                promptTokens = tokens.size,
                completionTokens = generatedCount,
                durationMs = System.currentTimeMillis() - startTime,
                providerId = "llama_cpp_native",
                modelId = _loadedModel?.id ?: "unknown"
            )
        )
    }.flowOn(Dispatchers.IO)

    override suspend fun cancel(requestId: String) {
        isCancelled.set(true)
    }

    override suspend fun unloadModel(): Result<Unit> = withContext(Dispatchers.IO) {
        if (contextHandle != 0L) {
            LlamaCppNativeBridge.nativeFree(contextHandle)
            contextHandle = 0L
            _loadedModel = null
        }
        Result.success(Unit)
    }
}
