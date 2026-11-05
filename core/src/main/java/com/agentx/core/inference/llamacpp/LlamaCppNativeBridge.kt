package com.agentx.core.inference.llamacpp

import android.util.Log

/**
 * JNI Native Bridge to llama.cpp C++ runtime.
 */
object LlamaCppNativeBridge {
    private const val TAG = "AgentX_LlamaBridge"

    val isNativeLoaded: Boolean by lazy {
        try {
            System.loadLibrary("agentx_llama")
            nativeInit()
            Log.i(TAG, "agentx_llama native library loaded successfully.")
            true
        } catch (e: UnsatisfiedLinkError) {
            Log.w(TAG, "agentx_llama native library not available for host JVM test runner: ${e.message}")
            false
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to load agentx_llama native library: ${e.message}")
            false
        }
    }

    external fun nativeInit(): Boolean
    external fun nativeLoadModel(modelPath: String, contextLength: Int, nThreads: Int): Long
    external fun nativeTokenize(contextHandle: Long, prompt: String): IntArray?
    external fun nativeEval(contextHandle: Long, tokens: IntArray): Boolean
    external fun nativeSampleToken(contextHandle: Long, tokenIndex: Int, temperature: Float, topP: Float): String?
    external fun nativeGetMemoryUsage(contextHandle: Long): Long
    external fun nativeFree(contextHandle: Long): Boolean
}
