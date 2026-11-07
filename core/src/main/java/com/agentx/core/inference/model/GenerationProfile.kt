package com.agentx.core.inference.model

import kotlinx.serialization.Serializable

@Serializable
enum class OutputFormat {
    TEXT,
    JSON,
    JSON_SCHEMA
}

@Serializable
enum class ProfilePreset {
    PRECISE,
    BALANCED,
    CREATIVE,
    CUSTOM
}

@Serializable
data class GenerationProfile(
    val name: String,
    val preset: ProfilePreset,
    val temperature: Float,
    val topP: Float,
    val topK: Int,
    val repetitionPenalty: Float,
    val seed: Int = 0,
    val maxTokens: Int = 1024,
    val systemPrompt: String? = null,
    val outputFormat: OutputFormat = OutputFormat.TEXT,
    val jsonSchema: String? = null,
    val stopSequences: List<String> = emptyList()
) {
    fun toGenerationRequest(
        prompt: String,
        overrideSystemPrompt: String? = null,
        tools: List<String> = emptyList(),
        preferredModelId: String? = null,
        executionMode: ExecutionMode = ExecutionMode.HYBRID
    ): GenerationRequest {
        val effectivePrompt = when (outputFormat) {
            OutputFormat.TEXT -> prompt
            OutputFormat.JSON -> {
                if (prompt.contains("JSON", ignoreCase = true)) prompt
                else "$prompt\n\n[Respond strictly with valid JSON only. Do not enclose in markdown codeblocks.]"
            }
            OutputFormat.JSON_SCHEMA -> {
                val schemaPrompt = jsonSchema?.let { "\nAdhere to the following JSON schema:\n$it" } ?: ""
                "$prompt$schemaPrompt\n\n[Respond strictly with valid JSON adhering to the specified schema.]"
            }
        }
        return GenerationRequest(
            prompt = effectivePrompt,
            systemPrompt = overrideSystemPrompt ?: systemPrompt,
            temperature = temperature,
            topP = topP,
            topK = topK,
            repetitionPenalty = repetitionPenalty,
            seed = seed,
            maxTokens = maxTokens,
            stopSequences = stopSequences,
            tools = tools,
            preferredModelId = preferredModelId,
            executionMode = executionMode
        )
    }

    companion object {
        val PRECISE = GenerationProfile(
            name = "Precise",
            preset = ProfilePreset.PRECISE,
            temperature = 0.2f,
            topP = 0.8f,
            topK = 20,
            repetitionPenalty = 1.15f,
            maxTokens = 1024,
            systemPrompt = "You are a precise, factual, and deterministic AI assistant. Provide concise, accurate responses without speculation."
        )

        val BALANCED = GenerationProfile(
            name = "Balanced",
            preset = ProfilePreset.BALANCED,
            temperature = 0.7f,
            topP = 0.9f,
            topK = 40,
            repetitionPenalty = 1.10f,
            maxTokens = 1024,
            systemPrompt = "You are a helpful, thoughtful, and capable AI assistant."
        )

        val CREATIVE = GenerationProfile(
            name = "Creative",
            preset = ProfilePreset.CREATIVE,
            temperature = 1.0f,
            topP = 0.95f,
            topK = 60,
            repetitionPenalty = 1.05f,
            maxTokens = 1024,
            systemPrompt = "You are an imaginative, creative, and expressive AI assistant."
        )

        fun forPreset(preset: ProfilePreset): GenerationProfile = when (preset) {
            ProfilePreset.PRECISE -> PRECISE
            ProfilePreset.BALANCED -> BALANCED
            ProfilePreset.CREATIVE -> CREATIVE
            ProfilePreset.CUSTOM -> BALANCED.copy(name = "Custom", preset = ProfilePreset.CUSTOM)
        }
    }
}
