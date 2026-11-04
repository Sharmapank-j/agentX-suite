package com.agentx.medical.core.inference

data class GatewayEndpoints(
    val rootUrl: String,           // e.g. "http://192.168.1.100:8080"
    val healthUrl: String,         // e.g. "http://192.168.1.100:8080/health"
    val modelsUrl: String,         // e.g. "http://192.168.1.100:8080/v1/models"
    val chatCompletionsUrl: String // e.g. "http://192.168.1.100:8080/v1/chat/completions"
)

object GatewayUrlNormalizer {

    fun normalize(rawInput: String?): GatewayEndpoints {
        val trimmed = rawInput?.trim()?.removeSurrounding("\"", "\"")?.removeSurrounding("'", "'") ?: ""
        if (trimmed.isBlank()) {
            return GatewayEndpoints(
                rootUrl = "http://192.168.1.100:8080",
                healthUrl = "http://192.168.1.100:8080/health",
                modelsUrl = "http://192.168.1.100:8080/v1/models",
                chatCompletionsUrl = "http://192.168.1.100:8080/v1/chat/completions"
            )
        }

        // Add scheme if omitted (e.g. 192.168.1.5:8080 -> http://192.168.1.5:8080)
        val withScheme = if (!trimmed.startsWith("http://", ignoreCase = true) &&
            !trimmed.startsWith("https://", ignoreCase = true)
        ) {
            "http://$trimmed"
        } else {
            trimmed
        }

        // Strip known endpoint paths from the end iteratively to obtain the root gateway URL
        var cleaned = withScheme.trimEnd('/')
        val suffixesToRemove = listOf(
            "/chat/completions",
            "/completions",
            "/v1/chat/completions",
            "/v1/models",
            "/models",
            "/health",
            "/api/v1/health",
            "/api/v1",
            "/v1"
        )

        var changed = true
        while (changed) {
            changed = false
            for (suffix in suffixesToRemove) {
                if (cleaned.endsWith(suffix, ignoreCase = true)) {
                    cleaned = cleaned.substring(0, cleaned.length - suffix.length).trimEnd('/')
                    changed = true
                }
            }
        }

        val root = cleaned.ifBlank { "http://192.168.1.100:8080" }
        return GatewayEndpoints(
            rootUrl = root,
            healthUrl = "$root/health",
            modelsUrl = "$root/v1/models",
            chatCompletionsUrl = "$root/v1/chat/completions"
        )
    }
}
