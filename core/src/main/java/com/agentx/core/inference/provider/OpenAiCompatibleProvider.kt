package com.agentx.core.inference.provider

import com.agentx.core.inference.model.ExecutionMode
import com.agentx.core.inference.model.GenerationEvent
import com.agentx.core.inference.model.GenerationRequest
import com.agentx.core.inference.model.ModelInfo
import com.agentx.core.inference.model.ProviderCapabilities
import com.agentx.core.inference.model.ProviderHealth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
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
import java.util.concurrent.ConcurrentHashMap

/**
 * Universal OpenAI-compatible remote inference provider.
 * Connects seamlessly to:
 * 1. AgentX Serve instances on Android phones/tablets via LAN/tunnel
 * 2. Local Ollama servers (port 11434)
 * 3. Local LM Studio servers (port 1234)
 * 4. LocalAI, vLLM, and standard cloud OpenAI endpoints
 */
class OpenAiCompatibleProvider(
    override val id: String = "openai_compatible",
    override val displayName: String = "OpenAI-Compatible Server",
    val baseUrl: String = "http://127.0.0.1:8080",
    val apiKey: String = "",
    val defaultModel: String = "default"
) : LLMProvider {

    override val executionMode: ExecutionMode = ExecutionMode.REMOTE

    private val activeConnections = ConcurrentHashMap<String, HttpURLConnection>()
    private var selectedModelId: String = defaultModel

    override suspend fun getSelectedModel(): ModelInfo? = withContext(Dispatchers.IO) {
        getModels().find { it.id == selectedModelId } ?: ModelInfo(
            id = selectedModelId,
            name = selectedModelId,
            isLocal = false,
            providerId = id
        )
    }

    override suspend fun selectModel(modelId: String): Result<Unit> {
        selectedModelId = modelId
        return Result.success(Unit)
    }

    override suspend fun capabilities(): ProviderCapabilities = ProviderCapabilities(
        supportsStreaming = true,
        supportsTools = true,
        supportsVision = false,
        supportsEmbeddings = false,
        supportsCancellation = true,
        maxContextTokens = 8192,
        isOfflineCapable = false
    )

    override suspend fun healthCheck(): ProviderHealth = withContext(Dispatchers.IO) {
        val start = System.currentTimeMillis()
        val normalized = normalizeUrl(baseUrl)
        try {
            val url = URL("$normalized/v1/models")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 4000
                readTimeout = 4000
                if (apiKey.isNotBlank()) {
                    setRequestProperty("Authorization", "Bearer ${apiKey.trim()}")
                }
            }
            val code = conn.responseCode
            val latency = System.currentTimeMillis() - start
            conn.disconnect()

            if (code in 200..299 || code == 404) {
                ProviderHealth(
                    isHealthy = true,
                    latencyMs = latency,
                    message = "Connected to $baseUrl ($code)"
                )
            } else {
                ProviderHealth(
                    isHealthy = false,
                    latencyMs = latency,
                    message = "HTTP $code from $url"
                )
            }
        } catch (e: Exception) {
            ProviderHealth(
                isHealthy = false,
                latencyMs = System.currentTimeMillis() - start,
                message = e.message ?: "Connection failed to $baseUrl"
            )
        }
    }

    override suspend fun getModels(): List<ModelInfo> = withContext(Dispatchers.IO) {
        val normalized = normalizeUrl(baseUrl)
        try {
            val url = URL("$normalized/v1/models")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 5000
                readTimeout = 8000
                if (apiKey.isNotBlank()) {
                    setRequestProperty("Authorization", "Bearer ${apiKey.trim()}")
                }
            }
            if (conn.responseCode in 200..299) {
                val body = conn.inputStream.bufferedReader().use { it.readText() }
                conn.disconnect()
                parseModelsJson(body)
            } else {
                conn.disconnect()
                listOf(ModelInfo(id = defaultModel, name = defaultModel, contextWindow = 4096, isLocal = false, providerId = id))
            }
        } catch (_: Exception) {
            listOf(ModelInfo(id = defaultModel, name = defaultModel, contextWindow = 4096, isLocal = false, providerId = id))
        }
    }

    override suspend fun generate(request: GenerationRequest): Flow<GenerationEvent> = flow {
        val startTime = System.currentTimeMillis()
        val normalized = normalizeUrl(baseUrl)
        val url = URL("$normalized/v1/chat/completions")
        val model = request.preferredModelId ?: defaultModel

        var emittedTokens = 0
        var conn: HttpURLConnection? = null

        try {
            conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                setRequestProperty("Accept", "text/event-stream, application/json")
                if (apiKey.isNotBlank()) {
                    setRequestProperty("Authorization", "Bearer ${apiKey.trim()}")
                    setRequestProperty("X-API-Key", apiKey.trim())
                }
                connectTimeout = 15_000
                readTimeout = 60_000
                doOutput = true
            }
            activeConnections[request.requestId] = conn

            val payload = buildJsonPayload(request, model)
            OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { writer ->
                writer.write(payload)
                writer.flush()
            }

            val code = conn.responseCode
            if (code !in 200..299) {
                val errorBody = conn.errorStream?.bufferedReader()?.use { it.readText() } ?: "HTTP $code"
                throw IllegalStateException("Server returned HTTP $code: $errorBody")
            }

            val contentType = conn.getHeaderField("Content-Type") ?: ""
            if (contentType.contains("text/event-stream")) {
                BufferedReader(InputStreamReader(conn.inputStream, Charsets.UTF_8)).use { reader ->
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        currentCoroutineContext().ensureActive()
                        val trimmed = line!!.trim()
                        if (trimmed.startsWith("data:")) {
                            val dataStr = trimmed.removePrefix("data:").trim()
                            if (dataStr == "[DONE]") break
                            if (dataStr.isNotBlank()) {
                                val token = extractDeltaToken(dataStr)
                                if (!token.isNullOrEmpty()) {
                                    emit(GenerationEvent.Token(token, emittedTokens))
                                    emittedTokens++
                                }
                            }
                        }
                    }
                }
            } else {
                val body = conn.inputStream.bufferedReader().use { it.readText() }
                val content = extractNonStreamingContent(body)
                if (content.isNotEmpty()) {
                    emit(GenerationEvent.Token(content, 0))
                    emittedTokens = 1
                }
            }

            val duration = System.currentTimeMillis() - startTime
            val promptEst = (request.prompt.length / 4) + ((request.systemPrompt?.length ?: 0) / 4)

            emit(
                GenerationEvent.Finished(
                    totalTokens = promptEst + emittedTokens,
                    promptTokens = promptEst,
                    completionTokens = emittedTokens,
                    durationMs = duration,
                    providerId = id,
                    modelId = model
                )
            )
        } catch (t: Throwable) {
            emit(GenerationEvent.Error(t, t.message ?: "Remote inference failed", id))
        } finally {
            activeConnections.remove(request.requestId)
            try {
                conn?.disconnect()
            } catch (_: Exception) {}
        }
    }.flowOn(Dispatchers.IO)

    override suspend fun cancel(requestId: String) {
        activeConnections[requestId]?.let { conn ->
            try {
                conn.disconnect()
            } catch (_: Exception) {}
            activeConnections.remove(requestId)
        }
    }

    private fun buildJsonPayload(request: GenerationRequest, model: String): String {
        val escapedPrompt = escapeJson(request.prompt)
        val messages = mutableListOf<String>()

        request.systemPrompt?.let { sys ->
            messages.add("""{"role": "system", "content": "${escapeJson(sys)}"}""")
        }
        messages.add("""{"role": "user", "content": "$escapedPrompt"}""")

        val messagesJson = messages.joinToString(",")

        return """
            {
                "model": "${escapeJson(model)}",
                "messages": [$messagesJson],
                "stream": true,
                "temperature": ${request.temperature},
                "max_tokens": ${request.maxTokens}
            }
        """.trimIndent()
    }

    private fun extractDeltaToken(jsonStr: String): String? {
        return try {
            val root = Json.parseToJsonElement(jsonStr).jsonObject
            val choices = root["choices"]?.jsonArray
            val firstChoice = choices?.firstOrNull()?.jsonObject
            val delta = firstChoice?.get("delta")?.jsonObject
            delta?.get("content")?.jsonPrimitive?.content
        } catch (_: Exception) {
            null
        }
    }

    private fun extractNonStreamingContent(jsonStr: String): String {
        return try {
            val root = Json.parseToJsonElement(jsonStr).jsonObject
            val choices = root["choices"]?.jsonArray
            val message = choices?.firstOrNull()?.jsonObject?.get("message")?.jsonObject
            message?.get("content")?.jsonPrimitive?.content ?: ""
        } catch (_: Exception) {
            ""
        }
    }

    private fun parseModelsJson(jsonStr: String): List<ModelInfo> {
        return try {
            val root = Json.parseToJsonElement(jsonStr).jsonObject
            val data = root["data"]?.jsonArray
            data?.mapNotNull { elem ->
                val obj = elem.jsonObject
                val modelId = obj["id"]?.jsonPrimitive?.content ?: return@mapNotNull null
                ModelInfo(id = modelId, name = modelId, contextWindow = 4096, isLocal = false, providerId = id)
            } ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun normalizeUrl(raw: String): String {
        var trimmed = raw.trim()
        if (!trimmed.startsWith("http://") && !trimmed.startsWith("https://")) {
            trimmed = "http://$trimmed"
        }
        return trimmed.trimEnd('/')
    }

    private fun escapeJson(str: String): String {
        return str
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\b", "\\b")
            .replace("\u000c", "\\f")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")
    }
}
