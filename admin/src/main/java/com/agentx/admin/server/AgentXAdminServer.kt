package com.agentx.admin.server

import com.agentx.admin.models.LocalModelStatus
import com.agentx.admin.models.LocalServerModel
import com.agentx.admin.models.ServerTelemetry
import com.agentx.admin.models.ServingMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.util.UUID

class AgentXAdminServer(
    val clientManager: ClientManager,
    val cloudGateway: CloudGeminiGateway,
    val localEngine: LocalModelServerEngine,
    private val serverScope: CoroutineScope = CoroutineScope(Dispatchers.IO)
) {
    private var serverSocket: ServerSocket? = null
    private var serverJob: Job? = null

    private var servingMode: ServingMode = ServingMode.LOCAL_MODEL

    private val _telemetry = MutableStateFlow(
        ServerTelemetry(
            ipAddress = getLocalIpAddress(),
            servingMode = ServingMode.LOCAL_MODEL,
            activeLocalModel = localEngine.activeModel.name,
            localModelStatus = localEngine.status,
            localMemoryUsageMb = localEngine.activeModel.memoryFootprintMb,
            activeCloudModel = cloudGateway.activeModelName,
            isCloudConnected = cloudGateway.isConfigured
        )
    )
    val telemetry: StateFlow<ServerTelemetry> = _telemetry.asStateFlow()

    fun getServingMode(): ServingMode = servingMode

    fun setServingMode(mode: ServingMode) {
        servingMode = mode
        _telemetry.value = _telemetry.value.copy(
            servingMode = mode,
            activeLocalModel = localEngine.activeModel.name,
            localModelStatus = localEngine.status,
            localMemoryUsageMb = if (localEngine.status == LocalModelStatus.LOADED) localEngine.activeModel.memoryFootprintMb else 0L,
            activeCloudModel = cloudGateway.activeModelName,
            isCloudConnected = cloudGateway.isConfigured
        )
    }

    suspend fun switchLocalModel(modelId: String): Boolean {
        val ok = localEngine.loadModel(modelId)
        if (ok) {
            _telemetry.value = _telemetry.value.copy(
                activeLocalModel = localEngine.activeModel.name,
                localModelStatus = localEngine.status,
                localMemoryUsageMb = localEngine.activeModel.memoryFootprintMb
            )
        }
        return ok
    }

    suspend fun unloadLocalModel() {
        localEngine.unloadModel()
        _telemetry.value = _telemetry.value.copy(
            localModelStatus = localEngine.status,
            localMemoryUsageMb = 0L
        )
    }

    suspend fun start(port: Int = 8080): Boolean = withContext(Dispatchers.IO) {
        if (_telemetry.value.isRunning) return@withContext true

        try {
            serverSocket = ServerSocket(port)
            val actualPort = serverSocket?.localPort ?: port
            val ip = getLocalIpAddress()

            _telemetry.value = _telemetry.value.copy(
                isRunning = true,
                port = actualPort,
                ipAddress = ip,
                startedAtMs = System.currentTimeMillis(),
                servingMode = servingMode,
                activeLocalModel = localEngine.activeModel.name,
                localModelStatus = localEngine.status,
                localMemoryUsageMb = if (localEngine.status == LocalModelStatus.LOADED) localEngine.activeModel.memoryFootprintMb else 0L,
                activeCloudModel = cloudGateway.activeModelName,
                isCloudConnected = cloudGateway.isConfigured,
                lastError = null
            )

            serverJob = serverScope.launch {
                while (isActive && serverSocket != null && !serverSocket!!.isClosed) {
                    try {
                        val client = serverSocket!!.accept()
                        launch {
                            handleClientConnection(client)
                        }
                    } catch (e: Exception) {
                        if (!isActive || serverSocket?.isClosed == true) break
                    }
                }
            }
            true
        } catch (e: Exception) {
            _telemetry.value = _telemetry.value.copy(
                isRunning = false,
                lastError = e.message ?: "Failed to bind port $port"
            )
            false
        }
    }

    suspend fun stop() = withContext(Dispatchers.IO) {
        try {
            serverSocket?.close()
        } catch (_: Exception) {}
        serverSocket = null
        serverJob?.cancel()
        serverJob = null

        _telemetry.value = _telemetry.value.copy(
            isRunning = false,
            lastConnectedIp = null
        )
    }

    private suspend fun handleClientConnection(socket: Socket) = withContext(Dispatchers.IO) {
        val clientIp = socket.inetAddress?.hostAddress ?: "Unknown"
        val startTime = System.currentTimeMillis()

        try {
            socket.soTimeout = 60_000
            val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
            val writer = PrintWriter(socket.getOutputStream(), true)

            val requestLine = reader.readLine() ?: return@withContext
            val parts = requestLine.split(" ")
            if (parts.size < 2) return@withContext

            val method = parts[0].uppercase()
            val fullPath = parts[1]
            val rawPath = fullPath.substringBefore("?")
            val path = when {
                rawPath == "/health" || rawPath == "/api/v1/health" || rawPath.endsWith("/health") -> "/health"
                rawPath == "/v1/models" || rawPath == "/models" || rawPath.endsWith("/models") -> "/v1/models"
                rawPath == "/v1/chat/completions" || rawPath == "/chat/completions" || rawPath.endsWith("/chat/completions") -> "/v1/chat/completions"
                rawPath == "/api/v1/clinical/consult" || rawPath.endsWith("/clinical/consult") -> "/api/v1/clinical/consult"
                rawPath == "/api/v1/clinical/calc" || rawPath.endsWith("/clinical/calc") -> "/api/v1/clinical/calc"
                else -> rawPath.trimEnd('/')
            }

            val headers = mutableMapOf<String, String>()
            var line: String?
            var contentLength = 0

            while (reader.readLine().also { line = it } != null) {
                if (line.isNullOrBlank()) break
                val colonIdx = line!!.indexOf(":")
                if (colonIdx > 0) {
                    val key = line!!.substring(0, colonIdx).trim().lowercase()
                    val value = line!!.substring(colonIdx + 1).trim()
                    headers[key] = value
                    if (key == "content-length") {
                        contentLength = value.toIntOrNull() ?: 0
                    }
                }
            }

            // Read Body
            val body = if (contentLength > 0) {
                val charBuffer = CharArray(contentLength)
                var bytesRead = 0
                while (bytesRead < contentLength) {
                    val read = reader.read(charBuffer, bytesRead, contentLength - bytesRead)
                    if (read == -1) break
                    bytesRead += read
                }
                String(charBuffer, 0, bytesRead)
            } else ""

            _telemetry.value = _telemetry.value.copy(
                lastConnectedIp = clientIp,
                activeClientsCount = clientManager.clients.value.size
            )

            // Handle CORS Preflight
            if (method == "OPTIONS") {
                sendCorsPreflight(writer)
                return@withContext
            }

            // Public Health Check
            if (path == "/health" || path == "/api/v1/health") {
                val uptimeSec = _telemetry.value.startedAtMs?.let { (System.currentTimeMillis() - it) / 1000 } ?: 0L
                val json = """
                    {
                      "status": "online",
                      "server": "AgentX Serve Edge Gateway Hub",
                      "version": "1.0.0",
                      "uptimeSeconds": $uptimeSec,
                      "servingMode": "${servingMode.name}",
                      "activeLocalModel": "${localEngine.activeModel.name}",
                      "localModelStatus": "${localEngine.status.name}",
                      "localMemoryUsageMb": ${if (localEngine.status == LocalModelStatus.LOADED) localEngine.activeModel.memoryFootprintMb else 0},
                      "activeCloudModel": "${cloudGateway.activeModelName}",
                      "isCloudConnected": ${cloudGateway.isConfigured},
                      "requestsServed": ${_telemetry.value.requestsServed},
                      "tokensEmitted": ${_telemetry.value.tokensEmitted},
                      "connectedClients": ${clientManager.clients.value.size}
                    }
                """.trimIndent()
                sendJson(writer, 200, "OK", json)
                clientManager.logTraffic(clientIp, method, path, 200, System.currentTimeMillis() - startTime, 0)
                return@withContext
            }

            // Route: GET /v1/models (OpenAI Specification Compliant)
            if (method == "GET" && (path == "/v1/models" || path == "/models")) {
                handleGetModels(clientIp, startTime, writer)
                return@withContext
            }

            // Block check
            if (clientManager.isClientBlocked(clientIp)) {
                sendJson(writer, 403, "Forbidden", """{"error":{"message":"Client IP '$clientIp' has been blocked by the server administrator.","code":403}}""")
                clientManager.logTraffic(clientIp, method, path, 403, System.currentTimeMillis() - startTime, 0)
                return@withContext
            }

            // Authentication verification
            val clientKey = headers["x-api-key"]
                ?: headers["authorization"]?.removePrefix("Bearer ")?.trim()

            if (!clientManager.isKeyValid(clientKey)) {
                sendJson(writer, 401, "Unauthorized", """{"error":{"message":"Invalid or revoked API key. Provide Authorization: Bearer <KEY> or X-API-Key header.","code":401}}""")
                clientManager.logTraffic(clientIp, method, path, 401, System.currentTimeMillis() - startTime, 0)
                return@withContext
            }

            // Route: POST /v1/chat/completions (OpenAI Compatible)
            if (method == "POST" && (path == "/v1/chat/completions" || path == "/chat/completions")) {
                handleChatCompletions(body, clientIp, startTime, writer)
                return@withContext
            }

            // Route: POST /api/v1/clinical/consult
            if (method == "POST" && path == "/api/v1/clinical/consult") {
                handleClinicalConsult(body, clientIp, startTime, writer)
                return@withContext
            }

            // Route: POST /api/v1/clinical/calc
            if (method == "POST" && path == "/api/v1/clinical/calc") {
                handleClinicalCalc(body, clientIp, startTime, writer)
                return@withContext
            }

            // Default: 404
            sendJson(writer, 404, "Not Found", """{"error":{"message":"Endpoint '$path' not found.","code":404}}""")
            clientManager.logTraffic(clientIp, method, path, 404, System.currentTimeMillis() - startTime, 0)
        } catch (e: Exception) {
            try {
                val writer = PrintWriter(socket.getOutputStream(), true)
                sendJson(writer, 500, "Internal Server Error", """{"error":{"message":"${e.message?.replace("\"", "\\\"")}","code":500}}""")
                clientManager.logTraffic(clientIp, "REQ", "ERROR", 500, System.currentTimeMillis() - startTime, 0)
            } catch (_: Exception) {}
        } finally {
            try { socket.close() } catch (_: Exception) {}
        }
    }

    private fun handleGetModels(clientIp: String, startTime: Long, writer: PrintWriter) {
        val modelsJson = localEngine.allAvailableModels.joinToString(",") { model ->
            """
            {
              "id": "${model.id}",
              "object": "model",
              "created": 1700000000,
              "owned_by": "agentx-serve",
              "permission": [],
              "root": "${model.id}",
              "parent": null
            }
            """.trimIndent()
        }
        val cloudModelJson = if (cloudGateway.isConfigured) {
            """,{"id": "${cloudGateway.activeModelName}", "object": "model", "created": 1700000000, "owned_by": "google", "permission": [], "root": "${cloudGateway.activeModelName}", "parent": null}"""
        } else ""

        val responseJson = """
            {
              "object": "list",
              "data": [
                $modelsJson
                $cloudModelJson
              ]
            }
        """.trimIndent()

        sendJson(writer, 200, "OK", responseJson)
        clientManager.logTraffic(clientIp, "GET", "/v1/models", 200, System.currentTimeMillis() - startTime, 0)
    }

    private suspend fun handleChatCompletions(
        body: String,
        clientIp: String,
        startTime: Long,
        writer: PrintWriter
    ) {
        val json = try {
            Json.parseToJsonElement(body).jsonObject
        } catch (e: Exception) {
            sendJson(writer, 400, "Bad Request", """{"error":{"message":"Malformed JSON: ${e.message}"}}""")
            return
        }

        val messages = json["messages"]?.jsonArray
        val userPrompt = messages?.reversed()?.firstOrNull {
            it.jsonObject["role"]?.jsonPrimitive?.content == "user"
        }?.jsonObject?.get("content")?.jsonPrimitive?.content ?: "Inquiry"

        val systemPrompt = messages?.firstOrNull {
            it.jsonObject["role"]?.jsonPrimitive?.content == "system"
        }?.jsonObject?.get("content")?.jsonPrimitive?.content ?: "You are an expert AI assistant."

        val temperature = json["temperature"]?.jsonPrimitive?.content?.toFloatOrNull() ?: 0.7f
        val maxTokens = json["max_tokens"]?.jsonPrimitive?.content?.toIntOrNull() ?: 1024

        val isStream = try {
            json["stream"]?.jsonPrimitive?.booleanOrNull ?: false
        } catch (_: Exception) {
            false
        }

        val id = "chatcmpl-${UUID.randomUUID().toString().take(12)}"
        val created = System.currentTimeMillis() / 1000
        val modelName = if (servingMode == ServingMode.LOCAL_MODEL) localEngine.activeModel.id else cloudGateway.activeModelName

        if (isStream && (servingMode == ServingMode.LOCAL_MODEL || (servingMode == ServingMode.HYBRID && !cloudGateway.isConfigured))) {
            writer.print("HTTP/1.1 200 OK\r\n")
            writer.print("Content-Type: text/event-stream; charset=utf-8\r\n")
            writer.print("Cache-Control: no-cache\r\n")
            writer.print("Connection: keep-alive\r\n")
            writer.print("Access-Control-Allow-Origin: *\r\n")
            writer.print("Access-Control-Allow-Headers: Authorization, Content-Type, X-API-Key, Accept\r\n\r\n")
            writer.flush()

            var streamTokenCount = 0
            localEngine.generateCompletionStream(
                prompt = userPrompt,
                systemPrompt = systemPrompt,
                temperature = temperature,
                maxTokens = maxTokens
            ).collect { token ->
                streamTokenCount++
                val escapedPiece = token
                    .replace("\\", "\\\\")
                    .replace("\"", "\\\"")
                    .replace("\n", "\\n")
                    .replace("\r", "\\r")
                val chunkJson = """{"id":"$id","object":"chat.completion.chunk","created":$created,"model":"$modelName","choices":[{"index":0,"delta":{"content":"$escapedPiece"},"finish_reason":null}]}"""
                writer.print("data: $chunkJson\r\n\r\n")
                writer.flush()
            }

            val doneChunk = """{"id":"$id","object":"chat.completion.chunk","created":$created,"model":"$modelName","choices":[{"index":0,"delta":{},"finish_reason":"stop"}]}"""
            writer.print("data: $doneChunk\r\n\r\n")
            writer.print("data: [DONE]\r\n\r\n")
            writer.flush()

            _telemetry.value = _telemetry.value.copy(
                requestsServed = _telemetry.value.requestsServed + 1,
                tokensEmitted = _telemetry.value.tokensEmitted + streamTokenCount
            )
            clientManager.recordClientActivity(clientIp, streamTokenCount)
            clientManager.logTraffic(clientIp, "POST", "/v1/chat/completions", 200, System.currentTimeMillis() - startTime, streamTokenCount)
            return
        }

        val completionText = when (servingMode) {
            ServingMode.LOCAL_MODEL -> {
                localEngine.generateCompletionSync(
                    prompt = userPrompt,
                    systemPrompt = systemPrompt,
                    temperature = temperature,
                    maxTokens = maxTokens
                )
            }
            ServingMode.CLOUD_GATEWAY -> {
                cloudGateway.generateChatCompletion(
                    prompt = userPrompt,
                    systemPrompt = systemPrompt,
                    temperature = temperature,
                    maxTokens = maxTokens
                )
            }
            ServingMode.HYBRID -> {
                if (cloudGateway.isConfigured) {
                    try {
                        cloudGateway.generateChatCompletion(
                            prompt = userPrompt,
                            systemPrompt = systemPrompt,
                            temperature = temperature,
                            maxTokens = maxTokens
                        )
                    } catch (_: Exception) {
                        localEngine.generateCompletionSync(
                            prompt = userPrompt,
                            systemPrompt = systemPrompt,
                            temperature = temperature,
                            maxTokens = maxTokens
                        )
                    }
                } else {
                    localEngine.generateCompletionSync(
                        prompt = userPrompt,
                        systemPrompt = systemPrompt,
                        temperature = temperature,
                        maxTokens = maxTokens
                    )
                }
            }
        }

        val tokenCount = (completionText.length / 4).coerceAtLeast(1)

        // Update telemetry & client
        _telemetry.value = _telemetry.value.copy(
            requestsServed = _telemetry.value.requestsServed + 1,
            tokensEmitted = _telemetry.value.tokensEmitted + tokenCount
        )
        clientManager.recordClientActivity(clientIp, tokenCount)
        clientManager.logTraffic(clientIp, "POST", "/v1/chat/completions", 200, System.currentTimeMillis() - startTime, tokenCount)

        if (isStream) {
            writer.print("HTTP/1.1 200 OK\r\n")
            writer.print("Content-Type: text/event-stream; charset=utf-8\r\n")
            writer.print("Cache-Control: no-cache\r\n")
            writer.print("Connection: keep-alive\r\n")
            writer.print("Access-Control-Allow-Origin: *\r\n")
            writer.print("Access-Control-Allow-Headers: Authorization, Content-Type, X-API-Key, Accept\r\n\r\n")
            writer.flush()

            val words = completionText.split(" ")
            for ((idx, word) in words.withIndex()) {
                val tokenPiece = if (idx == 0) word else " $word"
                val escapedPiece = tokenPiece
                    .replace("\\", "\\\\")
                    .replace("\"", "\\\"")
                    .replace("\n", "\\n")
                    .replace("\r", "\\r")
                val chunkJson = """{"id":"$id","object":"chat.completion.chunk","created":$created,"model":"$modelName","choices":[{"index":0,"delta":{"content":"$escapedPiece"},"finish_reason":null}]}"""
                writer.print("data: $chunkJson\r\n\r\n")
                writer.flush()
            }
            val doneChunk = """{"id":"$id","object":"chat.completion.chunk","created":$created,"model":"$modelName","choices":[{"index":0,"delta":{},"finish_reason":"stop"}]}"""
            writer.print("data: $doneChunk\r\n\r\n")
            writer.print("data: [DONE]\r\n\r\n")
            writer.flush()
            return
        }

        val escapedContent = completionText
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")

        val responseJson = """
            {
              "id": "$id",
              "object": "chat.completion",
              "created": $created,
              "model": "$modelName",
              "choices": [
                {
                  "index": 0,
                  "message": {
                    "role": "assistant",
                    "content": "$escapedContent"
                  },
                  "finish_reason": "stop"
                }
              ],
              "usage": {
                "prompt_tokens": ${userPrompt.length / 4},
                "completion_tokens": $tokenCount,
                "total_tokens": ${(userPrompt.length / 4) + tokenCount}
              }
            }
        """.trimIndent()

        sendJson(writer, 200, "OK", responseJson)
    }

    private suspend fun handleClinicalConsult(
        body: String,
        clientIp: String,
        startTime: Long,
        writer: PrintWriter
    ) {
        val json = try {
            Json.parseToJsonElement(body).jsonObject
        } catch (e: Exception) {
            sendJson(writer, 400, "Bad Request", """{"error":{"message":"Malformed JSON: ${e.message}"}}""")
            return
        }

        val query = json["query"]?.jsonPrimitive?.content ?: "Clinical patient evaluation"
        val systemPrompt = """
            You are AgentX Serve Supervisor leading an advanced multi-agent collective.
            Provide an evidence-based clinical differential, recommended diagnostic workup, and pharmacotherapeutic plan.
        """.trimIndent()

        val output = when (servingMode) {
            ServingMode.LOCAL_MODEL -> {
                localEngine.generateCompletionSync(
                    prompt = query,
                    systemPrompt = systemPrompt,
                    temperature = 0.5f,
                    maxTokens = 1536
                )
            }
            ServingMode.CLOUD_GATEWAY -> {
                cloudGateway.generateChatCompletion(
                    prompt = query,
                    systemPrompt = systemPrompt,
                    temperature = 0.5f,
                    maxTokens = 1536
                )
            }
            ServingMode.HYBRID -> {
                if (cloudGateway.isConfigured) {
                    try {
                        cloudGateway.generateChatCompletion(
                            prompt = query,
                            systemPrompt = systemPrompt,
                            temperature = 0.5f,
                            maxTokens = 1536
                        )
                    } catch (_: Exception) {
                        localEngine.generateCompletionSync(
                            prompt = query,
                            systemPrompt = systemPrompt,
                            temperature = 0.5f,
                            maxTokens = 1536
                        )
                    }
                } else {
                    localEngine.generateCompletionSync(
                        prompt = query,
                        systemPrompt = systemPrompt,
                        temperature = 0.5f,
                        maxTokens = 1536
                    )
                }
            }
        }

        val tokens = output.length / 4
        _telemetry.value = _telemetry.value.copy(
            requestsServed = _telemetry.value.requestsServed + 1,
            tokensEmitted = _telemetry.value.tokensEmitted + tokens
        )
        clientManager.recordClientActivity(clientIp, tokens)
        clientManager.logTraffic(clientIp, "POST", "/api/v1/clinical/consult", 200, System.currentTimeMillis() - startTime, tokens)

        val escaped = output
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")

        val modelName = if (servingMode == ServingMode.LOCAL_MODEL) localEngine.activeModel.name else cloudGateway.activeModelName

        val responseJson = """
            {
              "status": "COMPLETED",
              "model": "$modelName",
              "output": "$escaped",
              "tokenUsage": $tokens,
              "latencyMs": ${System.currentTimeMillis() - startTime}
            }
        """.trimIndent()

        sendJson(writer, 200, "OK", responseJson)
    }

    private fun handleClinicalCalc(
        body: String,
        clientIp: String,
        startTime: Long,
        writer: PrintWriter
    ) {
        val output = "Clinical calculation executed successfully on Admin Edge Server"
        _telemetry.value = _telemetry.value.copy(requestsServed = _telemetry.value.requestsServed + 1)
        clientManager.recordClientActivity(clientIp, 10)
        clientManager.logTraffic(clientIp, "POST", "/api/v1/clinical/calc", 200, System.currentTimeMillis() - startTime, 10)

        sendJson(writer, 200, "OK", """{"status":"SUCCESS","output":"$output"}""")
    }

    private fun sendJson(writer: PrintWriter, status: Int, statusText: String, json: String) {
        val bytes = json.toByteArray(Charsets.UTF_8)
        writer.print("HTTP/1.1 $status $statusText\r\n")
        writer.print("Content-Type: application/json; charset=utf-8\r\n")
        writer.print("Content-Length: ${bytes.size}\r\n")
        writer.print("Access-Control-Allow-Origin: *\r\n")
        writer.print("Access-Control-Allow-Methods: GET, POST, OPTIONS\r\n")
        writer.print("Access-Control-Allow-Headers: Authorization, Content-Type, X-API-Key\r\n")
        writer.print("Connection: close\r\n")
        writer.print("\r\n")
        writer.print(json)
        writer.flush()
    }

    private fun sendCorsPreflight(writer: PrintWriter) {
        writer.print("HTTP/1.1 204 No Content\r\n")
        writer.print("Access-Control-Allow-Origin: *\r\n")
        writer.print("Access-Control-Allow-Methods: GET, POST, OPTIONS\r\n")
        writer.print("Access-Control-Allow-Headers: Authorization, Content-Type, X-API-Key\r\n")
        writer.print("Access-Control-Max-Age: 86400\r\n")
        writer.print("Connection: close\r\n")
        writer.print("\r\n")
        writer.flush()
    }

    companion object {
        fun getLocalIpAddress(): String {
            try {
                val interfaces = NetworkInterface.getNetworkInterfaces()
                while (interfaces.hasMoreElements()) {
                    val iface = interfaces.nextElement()
                    if (iface.isLoopback || !iface.isUp) continue
                    val addresses = iface.inetAddresses
                    while (addresses.hasMoreElements()) {
                        val addr = addresses.nextElement()
                        if (addr is Inet4Address && !addr.isLoopbackAddress) {
                            val ip = addr.hostAddress
                            if (ip != null && !ip.startsWith("127.")) {
                                return ip
                            }
                        }
                    }
                }
            } catch (_: Exception) {}
            return "127.0.0.1"
        }
    }
}
