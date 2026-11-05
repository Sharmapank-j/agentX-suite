package com.agentx.core.inference.llamacpp

import android.util.Log

/**
 * JNI boundary for the pinned upstream llama.cpp runtime.
 * All model execution happens in native llama.cpp; Kotlin never fabricates tokens.
 */
object LlamaCppNativeBridge {
    private const val TAG = "AgentX_LlamaBridge"

    fun interface TokenCallback {
        /** Return false to stop generation. */
        fun onToken(text: String): Boolean
    }

    val isNativeLoaded: Boolean by lazy {
        try {
            System.loadLibrary("agentx_llama")
            nativeInit()
        } catch (error: Throwable) {
            Log.e(TAG, "Unable to load the llama.cpp runtime", error)
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
        prompt: String,
        maxTokens: Int,
        temperature: Float,
        topP: Float,
        callback: TokenCallback
    ): Int
    external fun nativeCancel(contextHandle: Long)
    external fun nativeGetMemoryUsage(contextHandle: Long): Long
    external fun nativeFree(contextHandle: Long): Boolean
}
