package com.agentx.core.inference.engine

import com.agentx.core.inference.llamacpp.LlamaCppNativeBridge
import com.agentx.core.inference.model.GenerationEvent
import com.agentx.core.inference.model.GenerationRequest
import com.agentx.core.inference.model.ModelInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * Executes GGUF weights with upstream llama.cpp through the shared JNI runtime.
 * The native library is built for each configured Android ABI by CMake/NDK.
 */
class LlamaCppInferenceEngine(
    private val nThreads: Int = Runtime.getRuntime().availableProcessors().coerceAtLeast(1),
    private val defaultContextLength: Int = 2048,
    private val gpuLayers: Int = 0
) : InferenceEngine {
    override val engineName = "llama.cpp"
    private val handle = AtomicLong(0L)
    private val cancelled = AtomicBoolean(false)
    @Volatile private var currentModel: ModelInfo? = null

    override val isModelLoaded: Boolean get() = handle.get() != 0L
    override val loadedModel: ModelInfo? get() = currentModel

    override suspend fun loadModel(model: ModelInfo): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            require(LlamaCppNativeBridge.isNativeLoaded) {
                "The native llama.cpp library is unavailable for this device ABI."
            }
            val path = File(model.description.ifBlank { model.id })
            require(path.isFile && path.canRead() && path.extension.equals("gguf", true)) {
                "Select a readable local .gguf file before loading the model."
            }
            unloadModel().getOrThrow()
            val nativeHandle = LlamaCppNativeBridge.nativeLoadModel(
                path.absolutePath,
                model.contextWindow.takeIf { it > 0 } ?: defaultContextLength,
                nThreads.coerceIn(1, 16),
                gpuLayers.coerceAtLeast(0)
            )
            check(nativeHandle != 0L) {
                "llama.cpp could not load this GGUF model. Check model integrity, RAM, and supported architecture."
            }
            handle.set(nativeHandle)
            currentModel = model
        }
    }

    override suspend fun generate(request: GenerationRequest): Flow<GenerationEvent> = channelFlow {
        val nativeHandle = handle.get()
        if (nativeHandle == 0L) {
            send(GenerationEvent.Error(IllegalStateException("No GGUF model is loaded"), "No GGUF model is loaded"))
            return@channelFlow
        }
        cancelled.set(false)
        val started = System.currentTimeMillis()
        var emitted = 0
        val prompt = buildString {
            request.systemPrompt?.takeIf { it.isNotBlank() }?.let { append("System: ").append(it).append("\n\n") }
            append("User: ").append(request.prompt).append("\nAssistant:")
        }
        try {
            emitted = withContext(Dispatchers.IO) {
                LlamaCppNativeBridge.nativeGenerate(
                    nativeHandle,
                    prompt,
                    request.maxTokens.coerceIn(1, 4096),
                    request.temperature.coerceIn(0f, 2f),
                    0.9f,
                    LlamaCppNativeBridge.TokenCallback { token ->
                        if (cancelled.get()) false
                        else trySend(GenerationEvent.Token(token)).isSuccess
                    }
                )
            }
            if (!cancelled.get()) {
                send(GenerationEvent.Finished(
                    totalTokens = emitted,
                    promptTokens = 0,
                    completionTokens = emitted,
                    durationMs = System.currentTimeMillis() - started,
                    providerId = "llama_cpp",
                    modelId = currentModel?.id ?: "unknown"
                ))
            }
        } catch (error: Throwable) {
            send(GenerationEvent.Error(error, error.message ?: "llama.cpp generation failed"))
        }
    }

    override suspend fun cancel(requestId: String) {
        cancelled.set(true)
        handle.get().takeIf { it != 0L }?.let(LlamaCppNativeBridge::nativeCancel)
    }

    override suspend fun unloadModel(): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            cancelled.set(true)
            val old = handle.getAndSet(0L)
            if (old != 0L) check(LlamaCppNativeBridge.nativeFree(old)) { "Native model release failed" }
            currentModel = null
        }
    }
}
