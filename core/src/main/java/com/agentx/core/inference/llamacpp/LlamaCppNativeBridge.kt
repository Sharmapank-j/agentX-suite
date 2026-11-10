package com.agentx.core.inference.llamacpp

/**
 * JNI boundary for the pinned upstream llama.cpp runtime.
 * All model execution happens in native llama.cpp; Kotlin never fabricates tokens.
 */
object LlamaCppNativeBridge {
    fun interface TokenCallback {
        /** Return false to stop generation. */
        fun onToken(text: String): Boolean
    }

    val isNativeLoaded: Boolean by lazy {
        try {
            System.loadLibrary("agentx_llama")
            nativeInit()
        } catch (_: Throwable) {
            false
        }
    }

    external fun nativeInit(): Boolean
    external fun nativeLoadModel(
        modelPath: String,
        contextLength: Int,
        nThreads: Int,
        gpuLayers: Int
    ): Long

    external fun nativeGenerate(
        contextHandle: Long,
        systemPrompt: String?,
        userPrompt: String,
        maxTokens: Int,
        temperature: Float,
        topP: Float,
        topK: Int = 40,
        repetitionPenalty: Float = 1.1f,
        seed: Int = 0,
        stopSequences: Array<String>? = null,
        callback: TokenCallback
    ): Int

    external fun nativeCancel(contextHandle: Long)
    external fun nativeGetMemoryUsage(contextHandle: Long): Long
    external fun nativeFree(contextHandle: Long): Boolean

    external fun nativeLoadVisionProjector(contextHandle: Long, mmprojPath: String): Boolean
    external fun nativeSupportsVision(contextHandle: Long): Boolean
    external fun nativeGenerateMultimodal(
        contextHandle: Long,
        systemPrompt: String?,
        userPrompt: String,
        imageBytes: ByteArray?,
        maxTokens: Int,
        temperature: Float,
        topP: Float,
        topK: Int = 40,
        repetitionPenalty: Float = 1.1f,
        seed: Int = 0,
        stopSequences: Array<String>? = null,
        callback: TokenCallback
    ): Int
}
