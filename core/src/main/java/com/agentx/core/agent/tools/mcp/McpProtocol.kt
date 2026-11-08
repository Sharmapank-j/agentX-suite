package com.agentx.core.agent.tools.mcp

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/**
 * Model Context Protocol (MCP) Standard JSON-RPC 2.0 Data Models.
 * Compatible with the Model Context Protocol Specification.
 */

@Serializable
data class McpRequest(
    val jsonrpc: String = "2.0",
    val id: String,
    val method: String,
    val params: JsonObject? = null
)

@Serializable
data class McpResponse(
    val jsonrpc: String = "2.0",
    val id: String,
    val result: JsonElement? = null,
    val error: McpError? = null
)

@Serializable
data class McpError(
    val code: Int,
    val message: String,
    val data: JsonElement? = null
)

@Serializable
data class McpImplementation(
    val name: String,
    val version: String
)

@Serializable
data class McpServerCapabilities(
    val tools: JsonObject? = null,
    val resources: JsonObject? = null,
    val prompts: JsonObject? = null,
    val logging: JsonObject? = null
)

@Serializable
data class McpInitializeResult(
    val protocolVersion: String = "2024-11-05",
    val capabilities: McpServerCapabilities = McpServerCapabilities(),
    val serverInfo: McpImplementation
)

@Serializable
data class McpTool(
    val name: String,
    val description: String = "",
    val inputSchema: JsonObject? = null
)

@Serializable
data class McpResource(
    val uri: String,
    val name: String,
    val description: String? = null,
    val mimeType: String? = null
)

@Serializable
data class McpResourceContent(
    val uri: String,
    val mimeType: String? = null,
    val text: String? = null,
    val blob: String? = null
)

@Serializable
data class McpPromptArgument(
    val name: String,
    val description: String? = null,
    val required: Boolean = false
)

@Serializable
data class McpPrompt(
    val name: String,
    val description: String? = null,
    val arguments: List<McpPromptArgument> = emptyList()
)

@Serializable
data class McpPromptMessage(
    val role: String,
    val content: JsonElement
)
