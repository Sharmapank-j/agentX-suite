package com.agentx.core

import com.agentx.core.agent.tools.CalculatorTool
import com.agentx.core.agent.tools.SystemTimeTool
import com.agentx.core.agent.tools.LocalMcpClient
import com.agentx.core.agent.tools.ToolRegistry
import com.agentx.core.agent.tools.mcp.McpRequest
import com.agentx.core.agent.tools.mcp.McpResponse
import com.agentx.core.agent.tools.mcp.McpServerRegistry
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class McpSubsystemTest {

    @Test
    fun testMcpProtocolSerialization() {
        val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
        val req = McpRequest(
            id = "req_1",
            method = "tools/call",
            params = buildJsonObject {
                put("name", "calculator")
                put("arguments", buildJsonObject {
                    put("expression", "25 + 75")
                })
            }
        )
        val serialized = json.encodeToString(req)
        assertTrue(serialized.contains("2.0"))
        assertTrue(serialized.contains("tools/call"))

        val deserialized = json.decodeFromString<McpRequest>(serialized)
        assertEquals("req_1", deserialized.id)
        assertEquals("tools/call", deserialized.method)
    }

    @Test
    fun testLocalMcpClientAndRegistryIntegration() = runTest {
        val calcTool = CalculatorTool()
        val timeTool = SystemTimeTool()
        val localMcpClient = LocalMcpClient(
            serverName = "TestEmbeddedServer",
            initialTools = listOf(calcTool, timeTool)
        )

        val toolRegistry = ToolRegistry()
        val mcpRegistry = McpServerRegistry(toolRegistry = toolRegistry)

        val registerResult = mcpRegistry.registerServer(localMcpClient)
        assertTrue(registerResult.isSuccess)
        assertEquals(2, registerResult.getOrNull()?.size)

        // Verify tools were bridged into toolRegistry
        val registeredCalc = toolRegistry.get("calculator")
        assertNotNull(registeredCalc)

        // Execute tool through toolRegistry
        val execResult = registeredCalc!!.execute("{\"expression\": \"10 * 10\"}")
        assertTrue(execResult.isSuccess)
        assertEquals("100.0", execResult.output)
    }
}
