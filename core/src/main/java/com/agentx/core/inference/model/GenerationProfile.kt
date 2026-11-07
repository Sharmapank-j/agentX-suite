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
data class ExtendedModelConfig(
    val preset: ProfilePreset = ProfilePreset.BALANCED,
    val maxTokens: Int = 1024,
    val topK: Int = 40,
    val repetitionPenalty: Float = 1.10f,
    val seed: Int = 0,
    val stopSequences: List<String> = emptyList(),
    val outputFormat: OutputFormat = OutputFormat.TEXT,
    val jsonSchema: String? = null,
    val systemPrompt: String? = null,
    val keepLoadedInRam: Boolean = true
) {
    fun serialize(): String {
        return try {
            kotlinx.serialization.json.Json.encodeToString(serializer(), this)
        } catch (_: Exception) {
            "preset=${preset.name};maxTokens=$maxTokens;topK=$topK;repPenalty=$repetitionPenalty;seed=$seed"
        }
    }

    companion object {
        private val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true; isLenient = true }

        fun parse(raw: String?): ExtendedModelConfig {
            if (raw.isNullOrBlank()) return ExtendedModelConfig()
            return try {
                if (raw.trimStart().startsWith("{")) {
                    json.decodeFromString(serializer(), raw)
                } else {
                    parseLegacyString(raw)
                }
            } catch (_: Exception) {
                parseLegacyString(raw)
            }
        }

        private fun parseLegacyString(raw: String): ExtendedModelConfig {
            var preset = ProfilePreset.BALANCED
            var maxTokens = 1024
            var topK = 40
            var repPenalty = 1.10f
            var seed = 0
            raw.split(";").forEach { part ->
                val kv = part.split("=", limit = 2)
                if (kv.size == 2) {
                    val key = kv[0].trim().lowercase()
                    val value = kv[1].trim()
                    when (key) {
                        "preset" -> try { preset = ProfilePreset.valueOf(value.uppercase()) } catch (_: Exception) {}
                        "maxtokens" -> maxTokens = value.toIntOrNull() ?: 1024
                        "topk" -> topK = value.toIntOrNull() ?: 40
                        "reppenalty", "penalty" -> repPenalty = value.toFloatOrNull() ?: 1.10f
                        "seed" -> seed = value.toIntOrNull() ?: 0
                    }
                }
            }
            return ExtendedModelConfig(
                preset = preset,
                maxTokens = maxTokens,
                topK = topK,
                repetitionPenalty = repPenalty,
                seed = seed
            )
        }
    }
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
