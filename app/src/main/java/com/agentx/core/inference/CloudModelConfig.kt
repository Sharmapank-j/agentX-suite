package com.agentx.core.inference

import kotlinx.serialization.Serializable

@Serializable
enum class CloudProvider(val displayName: String) {
    GEMINI("Google Gemini Cloud"),
    CUSTOM_GATEWAY("AgentX Admin Hub / Custom Gateway")
}

@Serializable
enum class InferenceMode(val displayName: String, val description: String) {
    LOCAL_ONLY("Local GGUF (Offline)", "Strictly on-device execution. 100% offline HIPAA compliant."),
    CLOUD_ONLY("Cloud Model (Online)", "Direct cloud inference with Google Gemini for maximum clinical accuracy."),
    HYBRID_AUTO("Hybrid Auto-Failover", "Uses high-precision cloud model when online; automatically falls back to local GGUF if offline.")
}

@Serializable
data class CloudModelConfig(
    val provider: CloudProvider = CloudProvider.GEMINI,
    val apiKey: String = "",
    val modelName: String = "gemini-2.5-flash",
    val customBaseUrl: String = "http://192.168.1.100:8080/v1",
    val inferenceMode: InferenceMode = InferenceMode.HYBRID_AUTO,
    val temperature: Float = 0.7f,
    val maxOutputTokens: Int = 1024,
    val isEnabled: Boolean = false
) {
    companion object {
        val AVAILABLE_GEMINI_MODELS = listOf(
            "gemini-2.5-flash" to "Gemini 2.5 Flash (Ultra-fast clinical triage & differential)",
            "gemini-2.5-pro" to "Gemini 2.5 Pro (Deep diagnostic reasoning & literature synthesis)",
            "gemini-2.0-flash" to "Gemini 2.0 Flash (Balanced speed & medical comprehension)"
        )
    }
}
