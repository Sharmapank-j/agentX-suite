package com.agentx.core.inference

import org.junit.Assert.assertEquals
import org.junit.Test

class CloudModelConfigApiKeyTest {

    @Test
    fun apiKeyFor_usesGeminiKeyWhenGeminiProviderIsSelected() {
        val config = CloudModelConfig(
            provider = CloudProvider.GEMINI,
            geminiApiKey = "gem-key",
            gatewayApiKey = "gw-key"
        )

        assertEquals("gem-key", config.apiKeyFor())
    }

    @Test
    fun apiKeyFor_usesGatewayKeyWhenGatewayProviderIsSelected() {
        val config = CloudModelConfig(
            provider = CloudProvider.CUSTOM_GATEWAY,
            geminiApiKey = "gem-key",
            gatewayApiKey = "gw-key"
        )

        assertEquals("gw-key", config.apiKeyFor())
    }

    @Test
    fun apiKeyFor_doesNotUseGeminiKeyForGatewayRequests() {
        val config = CloudModelConfig(
            provider = CloudProvider.CUSTOM_GATEWAY,
            geminiApiKey = "gem-key",
            gatewayApiKey = ""
        )

        assertEquals("", config.apiKeyFor())
    }

    @Test
    fun apiKeyFor_doesNotUseGatewayKeyForGeminiRequests() {
        val config = CloudModelConfig(
            provider = CloudProvider.GEMINI,
            geminiApiKey = "",
            gatewayApiKey = "gw-key"
        )

        assertEquals("", config.apiKeyFor())
    }
}
