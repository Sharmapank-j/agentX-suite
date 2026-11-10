package com.agentx.core.inference

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

class CloudInferenceEngine(
    var config: CloudModelConfig = CloudModelConfig()
) : InferenceEngine {

    var p2pChannel: com.agentx.core.protocol.p2p.P2PDataChannel? = null

    @Volatile
    private var activeConnection: HttpURLConnection? = null

    @Volatile
    private var isStopRequested: Boolean = false

    @Volatile
    private var currentStatus: ModelStatus = ModelStatus(
        state = ModelLifecycleState.NOT_LOADED,
        activeModelId = "cloud_${config.modelName}",
        activeModelName = config.modelName
    )

    override suspend fun loadModel(config: ModelConfig) {
        // Cloud models are hosted remotely and ready on-demand
        currentStatus = ModelStatus(
            state = ModelLifecycleState.LOADED,
            activeModelId = "cloud_${this.config.modelName}",
            activeModelName = "${this.config.provider.displayName} (${this.config.modelName})"
        )
    }

    override fun generate(request: InferenceRequest): Flow<GenerationEvent> = channelFlow {
        isStopRequested = false
        currentStatus = currentStatus.copy(state = ModelLifecycleState.GENERATING)
        val startTime = System.currentTimeMillis()
        var firstTokenTime = 0L
        var totalTokens = 0

        try {
            when (config.provider) {
                CloudProvider.GEMINI -> {
                    generateGeminiSse(request, onToken = { token ->
                        if (firstTokenTime == 0L) firstTokenTime = System.currentTimeMillis()
                        totalTokens++
                        send(GenerationEvent.Token(token))
                    })
                }
                CloudProvider.CUSTOM_GATEWAY -> {
                    generateOpenAiCompatible(request, onToken = { token ->
                        if (firstTokenTime == 0L) firstTokenTime = System.currentTimeMillis()
                        totalTokens++
                        send(GenerationEvent.Token(token))
                    })
                }
            }

            val duration = System.currentTimeMillis() - startTime
            val promptEstTokens = (request.prompt.length / 4) + ((request.systemPrompt?.length ?: 0) / 4)
            val ttft = if (firstTokenTime > 0L) firstTokenTime - startTime else duration
            val tokPerSec = if (duration > 0) (totalTokens * 1000.0) / duration else 0.0
            send(
                GenerationEvent.Finished(
                    totalTokens = promptEstTokens + totalTokens,
                    promptTokens = promptEstTokens,
                    completionTokens = totalTokens,
                    durationMs = duration,
                    tokensPerSecond = tokPerSec,
                    ttftMs = ttft
                )
            )
            currentStatus = currentStatus.copy(state = ModelLifecycleState.LOADED)
        } catch (e: Exception) {
            currentStatus = currentStatus.copy(state = ModelLifecycleState.ERROR, errorMessage = e.message)
            send(GenerationEvent.Error(e, e.message ?: "Cloud generation failed"))
        } finally {
            activeConnection?.disconnect()
            activeConnection = null
        }
    }.flowOn(Dispatchers.IO)

    private suspend fun generateGeminiSse(
        request: InferenceRequest,
        onToken: suspend (String) -> Unit
    ) = withContext(Dispatchers.IO) {
        val apiKey = config.apiKeyFor(CloudProvider.GEMINI)
        if (apiKey.isBlank()) {
            throw IllegalStateException("Google Gemini API Key is missing. Please configure your API key in Settings -> Cloud Models.")
        }

        val urlString = "https://generativelanguage.googleapis.com/v1beta/models/${config.modelName}:streamGenerateContent?alt=sse&key=$apiKey"
        val url = URL(urlString)
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            setRequestProperty("Accept", "text/event-stream")
            connectTimeout = 15_000
            readTimeout = 60_000
            doOutput = true
        }
        activeConnection = conn

        // Build Gemini JSON Request payload (Multimodal support)
        val escapedPrompt = escapeJson(request.prompt)
        val escapedSystem = request.systemPrompt?.let { escapeJson(it) }

        val systemBlock = if (!escapedSystem.isNullOrBlank()) {
            """
            "systemInstruction": {
                "parts": [{"text": "$escapedSystem"}]
            },
            """.trimIndent()
        } else ""

        val imagePart = if (!request.imageBase64.isNullOrBlank() && !request.imageMimeType.isNullOrBlank()) {
            """,
            {
                "inlineData": {
                    "mimeType": "${request.imageMimeType}",
                    "data": "${request.imageBase64}"
                }
            }"""
        } else ""

        val payload = """
            {
                $systemBlock
                "contents": [
                    {
                        "role": "user",
                        "parts": [
                            {"text": "$escapedPrompt"}$imagePart
                        ]
                    }
                ],
                "generationConfig": {
                    "temperature": ${request.temperature},
                    "maxOutputTokens": ${request.maxTokens}
                }
            }
        """.trimIndent()

        OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { writer ->
            writer.write(payload)
            writer.flush()
        }

        val responseCode = conn.responseCode
        if (responseCode !in 200..299) {
            val errorBody = conn.errorStream?.bufferedReader()?.use { it.readText() } ?: "HTTP $responseCode"
            throw IllegalStateException("Gemini API Error ($responseCode): $errorBody")
        }

        BufferedReader(InputStreamReader(conn.inputStream, Charsets.UTF_8)).use { reader ->
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                currentCoroutineContext().ensureActive()
                if (isStopRequested) break

                val trimmed = line!!.trim()
                if (trimmed.startsWith("data:")) {
                    val jsonStr = trimmed.removePrefix("data:").trim()
                    if (jsonStr.isNotBlank() && jsonStr != "[DONE]") {
                        try {
                            val parsed = Json.parseToJsonElement(jsonStr).jsonObject
                            val candidates = parsed["candidates"]?.jsonArray
                            val parts = candidates?.firstOrNull()?.jsonObject
                                ?.get("content")?.jsonObject
                                ?.get("parts")?.jsonArray

                            val text = parts?.firstOrNull()?.jsonObject
                                ?.get("text")?.jsonPrimitive?.content

                            if (!text.isNullOrEmpty()) {
                                onToken(text)
                            }
                        } catch (_: Exception) {
                            // Non-fatal chunk parsing error
                        }
                    }
                }
            }
        }
    }

    private suspend fun generateOpenAiCompatible(
        request: InferenceRequest,
        onToken: suspend (String) -> Unit
    ) = withContext(Dispatchers.IO) {
        // Attempt low-latency zero-relay WebRTC P2P DataChannel streaming first if available
        val p2p = p2pChannel
        if (p2p != null && p2p.isConnected() && request.imageBase64.isNullOrBlank()) {
            val reqId = java.util.UUID.randomUUID().toString()
            var streamedAnyToken = false
            val ok = p2p.streamRequest(
                com.agentx.core.protocol.p2p.P2PDataChannel.P2PRequest(
                    requestId = reqId,
                    model = config.modelName,
                    prompt = request.prompt,
                    systemPrompt = request.systemPrompt,
                    temperature = request.temperature
                ),
                onToken = { token ->
                    streamedAnyToken = true
                    kotlinx.coroutines.runBlocking { onToken(token) }
                },
                onFinish = {}
            )
            if (ok && streamedAnyToken) {
                return@withContext
            }
        }

        // Resilient HTTP/SSE fallback with automatic connection retry
        var lastErr: Exception? = null
        for (attempt in 1..2) {
            try {
                doHttpOpenAiCompatible(request, onToken)
                return@withContext
            } catch (e: Exception) {
                lastErr = e
                if (attempt < 2 && !isStopRequested) {
                    kotlinx.coroutines.delay(1200)
                } else {
                    throw e
                }
            }
        }
        if (lastErr != null) throw lastErr
    }

    private suspend fun doHttpOpenAiCompatible(
        request: InferenceRequest,
        onToken: suspend (String) -> Unit
    ) = withContext(Dispatchers.IO) {
        val endpoints = GatewayUrlNormalizer.normalize(config.customBaseUrl)
        val url = URL(endpoints.chatCompletionsUrl)
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            setRequestProperty("Accept", "application/json, text/event-stream")
            val gatewayApiKey = config.apiKeyFor(CloudProvider.CUSTOM_GATEWAY)
            val effectiveKey = gatewayApiKey.ifBlank { "ax-master-serve-key" }
            setRequestProperty("Authorization", "Bearer $effectiveKey")
            setRequestProperty("X-API-Key", effectiveKey)
            connectTimeout = 15_000
            readTimeout = 60_000
            doOutput = true
        }
        activeConnection = conn

        val escapedPrompt = escapeJson(request.prompt)
        val escapedSystem = request.systemPrompt?.let { escapeJson(it) } ?: "You are AgentX, an advanced autonomous AI personal assistant."

        val userContent = if (!request.imageBase64.isNullOrBlank() && !request.imageMimeType.isNullOrBlank()) {
            """[
                {"type": "text", "text": "$escapedPrompt"},
                {"type": "image_url", "image_url": {"url": "data:${request.imageMimeType};base64,${request.imageBase64}"}}
            ]""".trimIndent()
        } else {
            "\"$escapedPrompt\""
        }

        val payload = """
            {
                "model": "${config.modelName}",
                "messages": [
                    {"role": "system", "content": "$escapedSystem"},
                    {"role": "user", "content": $userContent}
                ],
                "stream": true,
                "temperature": ${request.temperature},
                "max_tokens": ${request.maxTokens}
            }
        """.trimIndent()

        OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { writer ->
            writer.write(payload)
            writer.flush()
        }

        val responseCode = conn.responseCode
        if (responseCode !in 200..299) {
            val errorBody = conn.errorStream?.bufferedReader()?.use { it.readText() } ?: "HTTP $responseCode"
            val friendlyMsg = when (responseCode) {
                401 -> "401 Unauthorized: Invalid or missing API Key. Check the API Key configured in Settings."
                403 -> "403 Forbidden: Client IP has been blocked by the AgentX Serve administrator."
                404 -> "404 Endpoint Not Found: Could not find endpoint at $url. Check your Gateway URL in Settings."
                else -> "Gateway API Error ($responseCode): $errorBody"
            }
            throw IllegalStateException(friendlyMsg)
        }

        val contentType = conn.getHeaderField("Content-Type") ?: ""
        if (contentType.contains("text/event-stream")) {
            // Streaming SSE
            BufferedReader(InputStreamReader(conn.inputStream, Charsets.UTF_8)).use { reader ->
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    currentCoroutineContext().ensureActive()
                    if (isStopRequested) break
                    val trimmed = line!!.trim()
                    if (trimmed.startsWith("data:")) {
                        val jsonStr = trimmed.removePrefix("data:").trim()
                        if (jsonStr == "[DONE]") break
                        if (jsonStr.isNotBlank()) {
                            try {
                                val parsed = Json.parseToJsonElement(jsonStr).jsonObject
                                val delta = parsed["choices"]?.jsonArray?.firstOrNull()?.jsonObject
                                    ?.get("delta")?.jsonObject
                                val content = delta?.get("content")?.jsonPrimitive?.content
                                if (!content.isNullOrEmpty()) {
                                    onToken(content)
                                }
                            } catch (_: Exception) {}
                        }
                    }
                }
            }
        } else {
            // Standard JSON response
            val responseText = conn.inputStream.bufferedReader().use { it.readText() }
            try {
                val parsed = Json.parseToJsonElement(responseText).jsonObject
                val messageContent = parsed["choices"]?.jsonArray?.firstOrNull()?.jsonObject
                    ?.get("message")?.jsonObject
                    ?.get("content")?.jsonPrimitive?.content

                if (!messageContent.isNullOrEmpty()) {
                    onToken(messageContent)
                } else {
                    val rawOutput = parsed["output"]?.jsonPrimitive?.content
                    if (!rawOutput.isNullOrEmpty()) {
                        onToken(rawOutput)
                    }
                }
            } catch (e: Exception) {
                onToken(responseText)
            }
        }
    }

    override suspend fun stopGeneration() {
        isStopRequested = true
        withContext(Dispatchers.IO) {
            try {
                activeConnection?.disconnect()
            } catch (_: Exception) {}
            activeConnection = null
        }
        currentStatus = currentStatus.copy(state = ModelLifecycleState.LOADED)
    }

    override suspend fun unloadModel() {
        stopGeneration()
        currentStatus = ModelStatus(state = ModelLifecycleState.NOT_LOADED)
    }

    override fun getModelStatus(): ModelStatus = currentStatus

    private fun escapeJson(input: String): String {
        return input
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")
    }
}
