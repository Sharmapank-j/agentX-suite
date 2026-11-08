package com.agentx.core.agent.tools.mcp

import com.agentx.core.agent.tools.McpClient
import com.agentx.core.agent.tools.ToolDefinition
import com.agentx.core.agent.tools.ToolExecutionResult
import com.agentx.core.agent.tools.ToolParameter
import com.agentx.core.agent.tools.ToolPermissionLevel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

/**
 * Standard HTTP/JSON-RPC 2.0 client for remote Model Context Protocol (MCP) servers.
 */
class HttpMcpClient(
    override val serverName: String,
    val endpointUrl: String,
    val authToken: String? = null,
    private val connectTimeoutMs: Int = 8000,
    private val readTimeoutMs: Int = 30000
) : McpClient {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
    }

    private var _isConnected = false
    override val isConnected: Boolean
        get() = _isConnected

    override suspend fun initialize(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val initParams = buildJsonObject {
                put("protocolVersion", "2024-11-05")
                put("capabilities", buildJsonObject {
                    put("tools", buildJsonObject {})
                    put("resources", buildJsonObject {})
                    put("prompts", buildJsonObject {})
                })
                put("clientInfo", buildJsonObject {
                    put("name", "AgentX-Client")
                    put("version", "1.4.0")
                })
            }

            val request = McpRequest(
                id = UUID.randomUUID().toString(),
                method = "initialize",
                params = initParams
            )

            val response = sendRpcRequest(request)
            if (response.error != null) {
                _isConnected = false
                Result.failure(Exception("MCP initialize failed: ${response.error.message} (code ${response.error.code})"))
            } else {
                _isConnected = true
                Result.success(Unit)
            }
        } catch (e: Exception) {
            _isConnected = false
            Result.failure(e)
        }
    }

    override suspend fun listTools(): List<ToolDefinition> = withContext(Dispatchers.IO) {
        if (!_isConnected) {
            initialize().getOrNull()
        }

        val request = McpRequest(
            id = UUID.randomUUID().toString(),
            method = "tools/list",
            params = buildJsonObject {}
        )

        try {
            val response = sendRpcRequest(request)
            val result = response.result?.jsonObject ?: return@withContext emptyList()
            val toolsArray = result["tools"]?.jsonArray ?: return@withContext emptyList()

            toolsArray.mapNotNull { element ->
                try {
                    val toolObj = element.jsonObject
                    val name = toolObj["name"]?.jsonPrimitive?.content ?: return@mapNotNull null
                    val description = toolObj["description"]?.jsonPrimitive?.contentOrNull ?: ""
                    val inputSchema = toolObj["inputSchema"]?.jsonObject

                    val parameters = parseSchemaParameters(inputSchema)

                    ToolDefinition(
                        name = name,
                        description = description,
                        permissionLevel = ToolPermissionLevel.SAFE,
                        parameters = parameters
                    )
                } catch (_: Exception) {
                    null
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    override suspend fun callTool(name: String, arguments: JsonObject): ToolExecutionResult = withContext(Dispatchers.IO) {
        val start = System.currentTimeMillis()
        val callId = UUID.randomUUID().toString()

        val params = buildJsonObject {
            put("name", name)
            put("arguments", arguments)
        }

        val request = McpRequest(
            id = callId,
            method = "tools/call",
            params = params
        )

        try {
            val response = sendRpcRequest(request)
            val duration = System.currentTimeMillis() - start

            if (response.error != null) {
                return@withContext ToolExecutionResult(
                    callId = callId,
                    toolName = name,
                    isSuccess = false,
                    output = "",
                    error = "MCP server error: ${response.error.message} (${response.error.code})",
                    executionDurationMs = duration
                )
            }

            val resultObj = response.result?.jsonObject
            val contentArray = resultObj?.get("content")?.jsonArray
            val outputText = if (contentArray != null && contentArray.isNotEmpty()) {
                contentArray.joinToString("\n") { item ->
                    item.jsonObject["text"]?.jsonPrimitive?.contentOrNull ?: item.toString()
                }
            } else {
                resultObj?.toString() ?: ""
            }

            val isError = resultObj?.get("isError")?.jsonPrimitive?.contentOrNull?.toBoolean() ?: false

            ToolExecutionResult(
                callId = callId,
                toolName = name,
                isSuccess = !isError,
                output = outputText,
                error = if (isError) outputText else null,
                executionDurationMs = duration
            )
        } catch (e: Exception) {
            ToolExecutionResult(
                callId = callId,
                toolName = name,
                isSuccess = false,
                output = "",
                error = "Network or MCP communication failure: ${e.message}",
                executionDurationMs = System.currentTimeMillis() - start
            )
        }
    }

    suspend fun listResources(): List<McpResource> = withContext(Dispatchers.IO) {
        val request = McpRequest(
            id = UUID.randomUUID().toString(),
            method = "resources/list",
            params = buildJsonObject {}
        )
        try {
            val response = sendRpcRequest(request)
            val resourcesArray = response.result?.jsonObject?.get("resources")?.jsonArray ?: return@withContext emptyList()
            resourcesArray.mapNotNull { element ->
                try {
                    val obj = element.jsonObject
                    McpResource(
                        uri = obj["uri"]?.jsonPrimitive?.content ?: return@mapNotNull null,
                        name = obj["name"]?.jsonPrimitive?.content ?: "",
                        description = obj["description"]?.jsonPrimitive?.contentOrNull,
                        mimeType = obj["mimeType"]?.jsonPrimitive?.contentOrNull
                    )
                } catch (_: Exception) {
                    null
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    suspend fun readResource(uri: String): Result<McpResourceContent> = withContext(Dispatchers.IO) {
        val request = McpRequest(
            id = UUID.randomUUID().toString(),
            method = "resources/read",
            params = buildJsonObject { put("uri", uri) }
        )
        try {
            val response = sendRpcRequest(request)
            val contentsArray = response.result?.jsonObject?.get("contents")?.jsonArray
            val first = contentsArray?.firstOrNull()?.jsonObject
                ?: return@withContext Result.failure(Exception("No resource content found"))

            Result.success(
                McpResourceContent(
                    uri = first["uri"]?.jsonPrimitive?.content ?: uri,
                    mimeType = first["mimeType"]?.jsonPrimitive?.contentOrNull,
                    text = first["text"]?.jsonPrimitive?.contentOrNull,
                    blob = first["blob"]?.jsonPrimitive?.contentOrNull
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun disconnect() {
        _isConnected = false
    }

    private fun sendRpcRequest(request: McpRequest): McpResponse {
        val url = URL(endpointUrl)
        val conn = url.openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            conn.setRequestProperty("Accept", "application/json")
            if (!authToken.isNullOrBlank()) {
                conn.setRequestProperty("Authorization", "Bearer $authToken")
            }
            conn.connectTimeout = connectTimeoutMs
            conn.readTimeout = readTimeoutMs
            conn.doOutput = true
            conn.doInput = true

            val requestBody = json.encodeToString(request)
            conn.outputStream.use { os ->
                os.write(requestBody.toByteArray(Charsets.UTF_8))
                os.flush()
            }

            val statusCode = conn.responseCode
            val inputStream = if (statusCode in 200..299) conn.inputStream else conn.errorStream
            val responseText = BufferedReader(InputStreamReader(inputStream ?: conn.inputStream)).use { it.readText() }

            return json.decodeFromString<McpResponse>(responseText)
        } finally {
            conn.disconnect()
        }
    }

    private fun parseSchemaParameters(schema: JsonObject?): List<ToolParameter> {
        if (schema == null) return emptyList()
        val properties = schema["properties"]?.jsonObject ?: return emptyList()
        val requiredList = schema["required"]?.jsonArray?.mapNotNull { it.jsonPrimitive.contentOrNull } ?: emptyList()

        return properties.map { (paramName, element) ->
            val paramObj = element.jsonObject
            val type = paramObj["type"]?.jsonPrimitive?.contentOrNull ?: "string"
            val desc = paramObj["description"]?.jsonPrimitive?.contentOrNull ?: ""
            val isRequired = requiredList.contains(paramName)
            ToolParameter(
                name = paramName,
                type = type,
                description = desc,
                isRequired = isRequired
            )
        }
    }
}
