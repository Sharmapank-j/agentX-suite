package com.agentx.core.inference

import com.agentx.core.inference.llamacpp.LlamaCppNativeBridge
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/** App-facing adapter over the shared upstream llama.cpp JNI runtime. */
class LlamaCppEngine : InferenceEngine {
    private val handle = AtomicLong(0L)
    private val cancelled = AtomicBoolean(false)
    @Volatile private var status = ModelStatus(ModelLifecycleState.NOT_LOADED)

    override suspend fun loadModel(config: ModelConfig) = withContext(Dispatchers.IO) {
        status = ModelStatus(ModelLifecycleState.LOADING, activeModelId = config.modelId)
        try {
            check(LlamaCppNativeBridge.isNativeLoaded) { "Native llama.cpp is unavailable for this device ABI." }
            val file = File(config.modelPath)
            require(file.isFile && file.canRead() && file.extension.equals("gguf", true)) {
                "Choose a readable local .gguf model file."
            }
            unloadModel()
            val loaded = LlamaCppNativeBridge.nativeLoadModel(
                file.absolutePath, config.contextLength, config.threads, config.gpuLayers
            )
            check(loaded != 0L) { "llama.cpp failed to load the GGUF model. Check RAM, file integrity, and model architecture." }
            handle.set(loaded)
            status = ModelStatus(
                ModelLifecycleState.LOADED, config.modelId, file.name,
                LlamaCppNativeBridge.nativeGetMemoryUsage(loaded)
            )
        } catch (error: Throwable) {
            status = ModelStatus(ModelLifecycleState.ERROR, config.modelId, errorMessage = error.message)
            throw error
        }
    }

    override fun generate(request: InferenceRequest): Flow<GenerationEvent> = channelFlow {
        val nativeHandle = handle.get()
        if (nativeHandle == 0L) {
            send(GenerationEvent.Error(IllegalStateException("No GGUF model is loaded"), "No GGUF model is loaded"))
            return@channelFlow
        }
        cancelled.set(false)
        status = status.copy(state = ModelLifecycleState.GENERATING)
        val started = System.currentTimeMillis()
        var count = 0
        val prompt = buildString {
            request.systemPrompt?.takeIf { it.isNotBlank() }?.let { append("System: ").append(it).append("\n\n") }
            append("User: ").append(request.prompt).append("\nAssistant:")
        }
        try {
            count = withContext(Dispatchers.IO) {
                LlamaCppNativeBridge.nativeGenerate(
                    nativeHandle, prompt, request.maxTokens.coerceIn(1, 4096),
                    request.temperature, request.topP,
                    LlamaCppNativeBridge.TokenCallback { token ->
                        if (cancelled.get()) false else trySend(GenerationEvent.Token(token)).isSuccess
                    }
                )
            }
            if (count < 0) throw IllegalStateException("llama.cpp failed during prompt evaluation or token generation.")
            if (!cancelled.get()) send(GenerationEvent.Finished(
                count, 0, count, System.currentTimeMillis() - started
            ))
        } catch (error: Throwable) {
            send(GenerationEvent.Error(error, error.message ?: "Native inference failed"))
        } finally {
            status = status.copy(state = if (handle.get() == 0L) ModelLifecycleState.NOT_LOADED else ModelLifecycleState.LOADED)
        }
    }

    override suspend fun stopGeneration() {
        cancelled.set(true)
        handle.get().takeIf { it != 0L }?.let(LlamaCppNativeBridge::nativeCancel)
    }

    override suspend fun unloadModel() = withContext(Dispatchers.IO) {
        cancelled.set(true)
        val old = handle.getAndSet(0L)
        if (old != 0L) LlamaCppNativeBridge.nativeFree(old)
        status = ModelStatus(ModelLifecycleState.NOT_LOADED)
    }

    override fun getModelStatus(): ModelStatus = status
}
