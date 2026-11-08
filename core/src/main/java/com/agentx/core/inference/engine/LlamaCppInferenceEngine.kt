package com.agentx.core.inference.engine

import com.agentx.core.device.CpuTopologyInspector
import com.agentx.core.inference.llamacpp.LlamaCppNativeBridge
import com.agentx.core.inference.model.GenerationEvent
import com.agentx.core.inference.model.GenerationRequest
import com.agentx.core.inference.model.ModelInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * Executes GGUF weights with upstream llama.cpp through the shared JNI runtime.
 * The native library is built for each configured Android ABI by CMake/NDK.
 */
class LlamaCppInferenceEngine(
    private val nThreads: Int = CpuTopologyInspector.inspect().recommendedInferenceThreads.coerceIn(1, 4),
    private val defaultContextLength: Int = 2048,
    private val gpuLayers: Int = 0
) : InferenceEngine {
    override val engineName = "llama.cpp"
    private val handle = AtomicLong(0L)
    private val cancelled = AtomicBoolean(false)
    private val loadMutex = Mutex()
    @Volatile private var currentModel: ModelInfo? = null

    override val isModelLoaded: Boolean get() = handle.get() != 0L
    override val loadedModel: ModelInfo? get() = currentModel

    override suspend fun loadModel(model: ModelInfo): Result<Unit> = withContext(Dispatchers.IO) {
        loadMutex.withLock {
            runCatching {
                if (handle.get() != 0L && currentModel?.modelPath == model.modelPath) {
                    return@runCatching
                }
                require(LlamaCppNativeBridge.isNativeLoaded) {
                    "The native llama.cpp library is unavailable for this device ABI."
                }
                val path = File(model.modelPath ?: "")
                require(path.exists() && path.isFile && path.extension.equals("gguf", true)) {
                    "Select a readable local .gguf file before loading the model: ${model.modelPath}"
                }
                unloadModelInternal()
                val targetContext = minOf(model.contextWindow.takeIf { it > 0 } ?: defaultContextLength, 3840).coerceAtLeast(512)
                val targetThreads = nThreads.coerceIn(1, 4)
                val nativeHandle = LlamaCppNativeBridge.nativeLoadModel(
                    path.absolutePath,
                    targetContext,
                    targetThreads,
                    gpuLayers.coerceAtLeast(0)
                )
                check(nativeHandle != 0L) {
                    "llama.cpp could not load this GGUF model. Check model integrity, RAM, and supported architecture."
                }
                handle.set(nativeHandle)
                currentModel = model
            }
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
        try {
            emitted = withContext(Dispatchers.IO) {
                LlamaCppNativeBridge.nativeGenerate(
                    contextHandle = nativeHandle,
                    systemPrompt = request.systemPrompt,
                    userPrompt = request.prompt,
                    maxTokens = request.maxTokens.coerceIn(1, 4096),
                    temperature = request.temperature.coerceIn(0f, 2f),
                    topP = request.topP.coerceIn(0.01f, 1.0f),
                    topK = request.topK.coerceAtLeast(1),
                    repetitionPenalty = request.repetitionPenalty.coerceAtLeast(1.0f),
                    seed = request.seed,
                    stopSequences = if (request.stopSequences.isNotEmpty()) request.stopSequences.toTypedArray() else null,
                    callback = LlamaCppNativeBridge.TokenCallback { token ->
                        if (cancelled.get()) {
                            false
                        } else {
                            trySend(GenerationEvent.Token(token))
                            !cancelled.get()
                        }
                    }
                )
            }
            check(emitted >= 0) { "llama.cpp failed during prompt evaluation or token generation." }
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
    }.buffer(kotlinx.coroutines.channels.Channel.UNLIMITED)

    override suspend fun cancel(requestId: String) {
        cancelled.set(true)
        handle.get().takeIf { it != 0L }?.let(LlamaCppNativeBridge::nativeCancel)
    }

    private fun unloadModelInternal() {
        cancelled.set(true)
        val old = handle.getAndSet(0L)
        if (old != 0L) {
            LlamaCppNativeBridge.nativeCancel(old)
            LlamaCppNativeBridge.nativeFree(old)
        }
        currentModel = null
    }

    override suspend fun unloadModel(): Result<Unit> = withContext(Dispatchers.IO) {
        loadMutex.withLock {
            runCatching {
                unloadModelInternal()
            }
        }
    }
}
