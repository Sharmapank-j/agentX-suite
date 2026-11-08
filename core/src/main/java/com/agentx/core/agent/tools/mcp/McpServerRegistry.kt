package com.agentx.core.agent.tools.mcp

import com.agentx.core.agent.tools.McpClient
import com.agentx.core.agent.tools.ToolDefinition
import com.agentx.core.agent.tools.ToolExecutionResult
import com.agentx.core.agent.tools.ToolExecutor
import com.agentx.core.agent.tools.ToolRegistry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import java.util.UUID

data class McpServerDescriptor(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val endpointUrl: String,
    val isLocal: Boolean = false,
    val isConnected: Boolean = false,
    val toolCount: Int = 0,
    val lastPingMs: Long = 0L,
    val errorMessage: String? = null
)

/**
 * Adapter that exposes any MCP tool as a native AgentX ToolExecutor.
 */
class McpToolExecutorAdapter(
    override val definition: ToolDefinition,
    private val client: McpClient
) : ToolExecutor {

    override suspend fun execute(argumentsJson: String): ToolExecutionResult {
        return try {
            val jsonElement = Json.parseToJsonElement(argumentsJson).jsonObject
            client.callTool(definition.name, jsonElement)
        } catch (e: Exception) {
            ToolExecutionResult(
                callId = UUID.randomUUID().toString(),
                toolName = definition.name,
                isSuccess = false,
                output = "",
                error = "Failed to parse arguments for MCP tool '${definition.name}': ${e.message}"
            )
        }
    }
}

/**
 * Central registry managing external and embedded Model Context Protocol (MCP) servers.
 */
class McpServerRegistry(
    private val toolRegistry: ToolRegistry? = null
) {
    private val servers = mutableMapOf<String, McpClient>()
    private val _serverDescriptors = MutableStateFlow<List<McpServerDescriptor>>(emptyList())
    val serverDescriptors: StateFlow<List<McpServerDescriptor>> = _serverDescriptors.asStateFlow()

    suspend fun registerServer(client: McpClient, endpointUrl: String = "in-process", isLocal: Boolean = true): Result<List<ToolDefinition>> {
        servers[client.serverName] = client

        val initResult = client.initialize()
        val isConnected = initResult.isSuccess
        var toolList: List<ToolDefinition> = emptyList()

        if (isConnected) {
            toolList = client.listTools()
            // If a ToolRegistry was supplied, register all MCP tools
            toolRegistry?.let { registry ->
                for (toolDef in toolList) {
                    registry.register(McpToolExecutorAdapter(toolDef, client))
                }
            }
        }

        updateDescriptor(
            McpServerDescriptor(
                name = client.serverName,
                endpointUrl = endpointUrl,
                isLocal = isLocal,
                isConnected = isConnected,
                toolCount = toolList.size,
                errorMessage = initResult.exceptionOrNull()?.message
            )
        )

        return if (isConnected) Result.success(toolList) else Result.failure(initResult.exceptionOrNull() ?: Exception("Failed to connect to MCP server"))
    }

    suspend fun unregisterServer(serverName: String) {
        val client = servers.remove(serverName)
        client?.disconnect()
        _serverDescriptors.value = _serverDescriptors.value.filter { it.name != serverName }
    }

    fun getServer(name: String): McpClient? = servers[name]

    fun getAllServers(): List<McpClient> = servers.values.toList()

    private fun updateDescriptor(descriptor: McpServerDescriptor) {
        val current = _serverDescriptors.value.toMutableList()
        val index = current.indexOfFirst { it.name == descriptor.name }
        if (index >= 0) {
            current[index] = descriptor
        } else {
            current.add(descriptor)
        }
        _serverDescriptors.value = current
    }
}
