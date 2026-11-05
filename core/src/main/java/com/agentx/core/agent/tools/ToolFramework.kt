package com.agentx.core.agent.tools

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

/**
 * Advanced Tool & MCP Framework specified in Section G of the Master Architecture.
 */

@Serializable
enum class ToolPermissionLevel {
    /** Safe read-only deterministic actions (e.g. math calculator, time, JSON formatting) */
    SAFE,
    /** Actions reading local data or external read-only web searches */
    SENSITIVE,
    /** Actions writing files, executing network mutations, or modifying system state (requires explicit human approval) */
    DANGEROUS
}

@Serializable
data class ToolParameter(
    val name: String,
    val type: String, // "string", "number", "boolean", "object", "array"
    val description: String,
    val isRequired: Boolean = true,
    val enumValues: List<String> = emptyList()
)

@Serializable
data class ToolDefinition(
    val name: String,
    val description: String,
    val permissionLevel: ToolPermissionLevel = ToolPermissionLevel.SAFE,
    val parameters: List<ToolParameter> = emptyList()
)

@Serializable
data class ToolExecutionRequest(
    val callId: String,
    val toolName: String,
    val argumentsJson: String,
    val requiresApproval: Boolean = false,
    val isApproved: Boolean = false
)

@Serializable
data class ToolExecutionResult(
    val callId: String,
    val toolName: String,
    val isSuccess: Boolean,
    val output: String,
    val error: String? = null,
    val executionDurationMs: Long = 0L
)

interface ToolExecutor {
    val definition: ToolDefinition
    suspend fun execute(argumentsJson: String): ToolExecutionResult
}

class ToolRegistry {
    private val tools = mutableMapOf<String, ToolExecutor>()

    fun register(executor: ToolExecutor) {
        tools[executor.definition.name] = executor
    }

    fun get(name: String): ToolExecutor? = tools[name]

    fun getAllDefinitions(): List<ToolDefinition> = tools.values.map { it.definition }

    suspend fun executeCall(request: ToolExecutionRequest): ToolExecutionResult {
        val executor = tools[request.toolName]
            ?: return ToolExecutionResult(
                callId = request.callId,
                toolName = request.toolName,
                isSuccess = false,
                output = "",
                error = "Tool '${request.toolName}' is not registered."
            )

        // Enforce safety permissions: dangerous operations MUST be approved
        if (executor.definition.permissionLevel == ToolPermissionLevel.DANGEROUS && !request.isApproved) {
            return ToolExecutionResult(
                callId = request.callId,
                toolName = request.toolName,
                isSuccess = false,
                output = "",
                error = "Execution blocked: Tool '${request.toolName}' is classified as DANGEROUS and requires explicit user approval."
            )
        }

        // Validate JSON arguments syntax
        try {
            Json.parseToJsonElement(request.argumentsJson).jsonObject
        } catch (e: Exception) {
            return ToolExecutionResult(
                callId = request.callId,
                toolName = request.toolName,
                isSuccess = false,
                output = "",
                error = "Invalid JSON arguments: ${e.message}"
            )
        }

        val start = System.currentTimeMillis()
        return try {
            val res = executor.execute(request.argumentsJson)
            res.copy(executionDurationMs = System.currentTimeMillis() - start)
        } catch (e: Throwable) {
            ToolExecutionResult(
                callId = request.callId,
                toolName = request.toolName,
                isSuccess = false,
                output = "",
                error = "Execution failed: ${e.message}",
                executionDurationMs = System.currentTimeMillis() - start
            )
        }
    }
}

/**
 * Model Context Protocol (MCP) Client contract for connecting external tool servers.
 */
interface McpClient {
    val serverName: String
    val isConnected: Boolean

    suspend fun initialize(): Result<Unit>
    suspend fun listTools(): List<ToolDefinition>
    suspend fun callTool(name: String, arguments: JsonObject): ToolExecutionResult
    suspend fun disconnect()
}
