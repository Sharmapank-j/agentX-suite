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

                // Locate and load multimodal vision projector if available
                val projFile = config.mmprojPath?.let { File(it) }?.takeIf { it.exists() && it.isFile }
                    ?: file.parentFile?.listFiles()?.firstOrNull { f ->
                        f.isFile && f.extension.equals("gguf", true) && f.name.contains("mmproj", ignoreCase = true)
                    }
                var visionLoaded = false
                if (projFile != null) {
                    visionLoaded = LlamaCppNativeBridge.nativeLoadVisionProjector(loaded, projFile.absolutePath)
                }
                val supportsVision = visionLoaded || LlamaCppNativeBridge.nativeSupportsVision(loaded)

                status = ModelStatus(
                    state = ModelLifecycleState.LOADED,
                    activeModelId = config.modelId,
                    activeModelName = file.name,
                    memoryUsageBytes = LlamaCppNativeBridge.nativeGetMemoryUsage(loaded),
                    supportsVision = supportsVision,
                    mmprojPath = projFile?.absolutePath
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
        var firstTokenTime = 0L
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
            val imageBytes = request.imageBase64?.let {
                try {
                    android.util.Base64.decode(it, android.util.Base64.DEFAULT)
                } catch (_: Throwable) { null }
            }
            val hasVision = imageBytes != null && (status.supportsVision || status.mmprojPath != null)

            count = withContext(Dispatchers.IO) {
                if (hasVision) {
                    LlamaCppNativeBridge.nativeGenerateMultimodal(
                        contextHandle = nativeHandle,
                        systemPrompt = request.systemPrompt,
                        userPrompt = request.prompt,
                        imageBytes = imageBytes,
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
                                if (firstTokenTime == 0L) {
                                    firstTokenTime = System.currentTimeMillis()
                                }
                                trySend(GenerationEvent.Token(token))
                                !cancelled.get()
                            }
                        }
                    )
                } else {
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
                                if (firstTokenTime == 0L) {
                                    firstTokenTime = System.currentTimeMillis()
                                }
                                trySend(GenerationEvent.Token(token))
                                !cancelled.get()
                            }
                        }
                    )
                }
            }
            if (count < 0) throw IllegalStateException("llama.cpp failed during prompt evaluation or token generation.")
            if (!cancelled.get()) {
                val durationMs = System.currentTimeMillis() - started
                val ttft = if (firstTokenTime > 0L) firstTokenTime - started else durationMs
                val tokPerSec = if (durationMs > 0) (count * 1000.0) / durationMs else 0.0
                send(GenerationEvent.Finished(
                    totalTokens = count,
                    promptTokens = 0,
                    completionTokens = count,
                    durationMs = durationMs,
                    tokensPerSecond = tokPerSec,
                    ttftMs = ttft
                ))
            }
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
