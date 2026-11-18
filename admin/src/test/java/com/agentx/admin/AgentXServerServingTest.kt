package com.agentx.admin

import com.agentx.admin.server.ClientManager
import com.agentx.admin.server.CloudGeminiGateway
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AgentXServerServingTest {

    @Test
    fun clientManagerAcceptsMasterEcosystemKeyAndEnforcesAuth() {
        val manager = ClientManager()
        // Zero-Trust: When auth is enforced by default, null/blank/invalid keys must be rejected
        assertFalse("Null key should be rejected when auth is enforced", manager.isKeyValid(null))
        assertFalse("Blank key should be rejected when auth is enforced", manager.isKeyValid(""))
        assertFalse("Invalid key should be rejected", manager.isKeyValid("invalid-token-123"))
        assertTrue("Master ecosystem key should be valid for internal checks", manager.isKeyValid("ax-master-serve-key"))
        assertTrue("Bearer prefixed master key should be valid for loopback", manager.isKeyValid("Bearer ax-master-serve-key", "127.0.0.1"))
        assertTrue("Master key should be valid for LAN subnet", manager.isKeyValid("ax-master-serve-key", "192.168.1.100"))

        // Security check: Master developer key MUST be rejected when requested from external WAN IPs
        assertFalse("Master key must be rejected from external public WAN IPs", manager.isKeyValid("ax-master-serve-key", "203.0.113.195"))
        assertFalse("Master key must be rejected from external public WAN IPs", manager.isKeyValid("Bearer ax-master-serve-key", "118.95.186.30"))

        // When auth enforcement is disabled (e.g. permissive mode)
        manager.isAuthEnforced = false
        assertTrue("Null key should be accepted when auth is disabled", manager.isKeyValid(null))
        assertTrue("Blank key should be accepted when auth is disabled", manager.isKeyValid(""))
    }

    @Test
    fun clientManagerRespectsRevokedKeys() {
        val manager = ClientManager()
        val created = manager.createApiKey("Test Key")
        assertTrue(manager.isKeyValid(created.key))

        manager.revokeApiKey(created.key)
        assertFalse(manager.isKeyValid(created.key))
    }

    @Test
    fun cloudGatewayProvidesGracefulEdgeSimulationWhenUnconfigured() = runTest {
        val gateway = CloudGeminiGateway()
        gateway.geminiApiKey = ""
        val response = gateway.generateChatCompletion("What is the system status?")
        assertTrue(response.contains("[AgentX Serve Gateway — Edge Simulation Mode]"))
        assertTrue(response.contains("What is the system status?"))

        val streamTokens = gateway.generateChatCompletionStream("What is the system status?").toList()
        assertTrue(streamTokens.isNotEmpty())
        val combined = streamTokens.joinToString("")
        assertTrue(combined.contains("[AgentX Serve Gateway — Edge Simulation Mode]"))
    }

    @Test
    fun extractMessageContentHandlesBothPrimitiveAndMultimodalArray() {
        // 1. Primitive string test
        val primitiveJson = Json.parseToJsonElement("\"Explain quantum computing\"")
        val (text1, img1) = parseContent(primitiveJson)
        assertEquals("Explain quantum computing", text1)
        assertEquals(null, img1)

        // 2. Multimodal array test
        val arrayJson = Json.parseToJsonElement("""
            [
                {"type": "text", "text": "What does this diagram show?"},
                {"type": "image_url", "image_url": {"url": "data:image/jpeg;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNk+M9QDwADhgGAWjR9awAAAABJRU5ErkJggg=="}}
            ]
        """.trimIndent())
        val (text2, img2) = parseContent(arrayJson)
        assertEquals("What does this diagram show?", text2)
        assertNotNull(img2)
        assertTrue(img2!!.startsWith("iVBORw0KGgo"))
    }

    private fun parseContent(element: kotlinx.serialization.json.JsonElement?): Pair<String, String?> {
        if (element == null) return Pair("", null)
        if (element is kotlinx.serialization.json.JsonPrimitive) {
            return Pair(element.content, null)
        }
        if (element is kotlinx.serialization.json.JsonArray) {
            val textBuilder = StringBuilder()
            var imageBase64: String? = null
            element.forEach { item ->
                if (item is kotlinx.serialization.json.JsonObject) {
                    val type = item["type"]?.jsonPrimitive?.content ?: ""
                    if (type == "text") {
                        val t = item["text"]?.jsonPrimitive?.content ?: ""
                        textBuilder.append(t)
                    } else if (type == "image_url") {
                        val url = item["image_url"]?.jsonObject?.get("url")?.jsonPrimitive?.content ?: ""
                        if (url.startsWith("data:") && url.contains(";base64,")) {
                            imageBase64 = url.substringAfter(";base64,")
                        }
                    }
                }
            }
            return Pair(textBuilder.toString(), imageBase64)
        }
        return Pair(element.toString(), null)
    }
}
