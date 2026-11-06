package com.agentx.core.inference

import kotlinx.serialization.Serializable

@Serializable
enum class CloudProvider(val displayName: String) {
    GEMINI("Google Gemini Cloud"),
    CUSTOM_GATEWAY("AgentX Admin Hub / Custom Gateway")
}

@Serializable
enum class InferenceMode(val displayName: String, val description: String) {
    LOCAL_ONLY("Local AI (Unavailable)", "Local inference is unavailable until a real GGUF runtime is integrated and validated."),
    CLOUD_ONLY("Cloud Model (Online)", "Direct cloud inference with Google Gemini for maximum reasoning accuracy."),
    HYBRID_AUTO("Hybrid (Local Runtime Required)", "Uses the configured cloud provider; local fallback remains unavailable until a real GGUF runtime is integrated.")
}

@Serializable
data class CloudModelConfig(
    val provider: CloudProvider = CloudProvider.GEMINI,
    val geminiApiKey: String = "",
    val gatewayApiKey: String = "",
    val apiKey: String = "",
    val modelName: String = "gemini-2.5-flash",
    val customBaseUrl: String = "http://192.168.1.100:8080/v1",
    val inferenceMode: InferenceMode = InferenceMode.HYBRID_AUTO,
    val temperature: Float = 0.7f,
    val maxOutputTokens: Int = 1024,
    val isEnabled: Boolean = false
) {
    fun apiKeyFor(provider: CloudProvider = this.provider): String {
        return when (provider) {
            CloudProvider.GEMINI -> geminiApiKey.trim()
            CloudProvider.CUSTOM_GATEWAY -> gatewayApiKey.trim()
        }
    }

    companion object {
        val AVAILABLE_GEMINI_MODELS = listOf(
            "gemini-2.5-flash" to "Gemini 2.5 Flash (Ultra-fast coding & multimodal intelligence)",
            "gemini-2.5-pro" to "Gemini 2.5 Pro (Deep multi-step reasoning, systems architecture & math)",
            "gemini-2.0-flash" to "Gemini 2.0 Flash (Balanced conversational speed & comprehension)"
        )
    }
}
