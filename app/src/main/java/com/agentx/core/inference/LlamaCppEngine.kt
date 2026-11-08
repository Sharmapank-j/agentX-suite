package com.agentx.core.inference

import com.agentx.core.inference.llamacpp.LlamaCppNativeBridge
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

/** App-facing adapter over the shared upstream llama.cpp JNI runtime. */
class LlamaCppEngine : InferenceEngine {
    private val handle = AtomicLong(0L)
    private val cancelled = AtomicBoolean(false)
    private val loadMutex = Mutex()
    @Volatile private var status = ModelStatus(ModelLifecycleState.NOT_LOADED)

    override suspend fun loadModel(config: ModelConfig) = withContext(Dispatchers.IO) {
        loadMutex.withLock {
            if (handle.get() != 0L && status.activeModelId == config.modelId &&
                (status.state == ModelLifecycleState.LOADED || status.state == ModelLifecycleState.GENERATING)) {
                return@withLock
            }
            status = ModelStatus(ModelLifecycleState.LOADING, activeModelId = config.modelId)
            try {
                check(LlamaCppNativeBridge.isNativeLoaded) { "Native llama.cpp is unavailable for this device ABI." }
                val file = File(config.modelPath)
                require(file.exists() && file.isFile && file.extension.equals("gguf", true)) {
                    "Choose a readable local .gguf model file: ${config.modelPath}"
                }
                unloadModelInternal()
                val targetContext = minOf(config.contextLength, 3840).coerceAtLeast(512)
                val targetThreads = config.threads.coerceIn(1, 4)
                val loaded = LlamaCppNativeBridge.nativeLoadModel(
                    file.absolutePath, targetContext, targetThreads, config.gpuLayers.coerceAtLeast(0)
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
    }

    companion object {
        @Volatile var appContext: android.content.Context? = null
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
        val wakeLock = appContext?.let { ctx ->
            try {
                val pm = ctx.getSystemService(android.content.Context.POWER_SERVICE) as? android.os.PowerManager
                pm?.newWakeLock(android.os.PowerManager.PARTIAL_WAKE_LOCK, "agentx:inference_wakelock")?.apply {
                    setReferenceCounted(false)
                    acquire(15 * 60 * 1000L)
                }
            } catch (_: Throwable) { null }
        }
        try {
            count = withContext(Dispatchers.IO) {
                LlamaCppNativeBridge.nativeGenerate(
                    contextHandle = nativeHandle,
                    systemPrompt = request.systemPrompt,
                    userPrompt = request.prompt,
                    maxTokens = request.maxTokens.coerceIn(1, 4096),
                    temperature = request.temperature.coerceIn(0f, 2f),
                    topP = request.topP.coerceIn(0.01f, 1.0f),
                    topK = request.topK.coerceAtLeast(1),
                    repetitionPenalty = request.repeatPenalty.coerceAtLeast(1.0f),
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
            if (count < 0) throw IllegalStateException("llama.cpp failed during prompt evaluation or token generation.")
            if (!cancelled.get()) send(GenerationEvent.Finished(
                count, 0, count, System.currentTimeMillis() - started
            ))
        } catch (error: Throwable) {
            send(GenerationEvent.Error(error, error.message ?: "Native inference failed"))
        } finally {
            try {
                if (wakeLock?.isHeld == true) wakeLock.release()
            } catch (_: Throwable) { }
            status = status.copy(state = if (handle.get() == 0L) ModelLifecycleState.NOT_LOADED else ModelLifecycleState.LOADED)
        }
    }.buffer(kotlinx.coroutines.channels.Channel.UNLIMITED)

    override suspend fun stopGeneration() {
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
        status = ModelStatus(ModelLifecycleState.NOT_LOADED)
    }

    override suspend fun unloadModel() = withContext(Dispatchers.IO) {
        loadMutex.withLock {
            unloadModelInternal()
        }
    }

    override fun getModelStatus(): ModelStatus = status
}
