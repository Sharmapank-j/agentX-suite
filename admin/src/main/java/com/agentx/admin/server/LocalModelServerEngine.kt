package com.agentx.admin.server

import android.content.Context
import android.os.Environment
import com.agentx.admin.models.LocalModelStatus
import com.agentx.admin.models.LocalServerModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

/**
 * On-Device Local Model Server Engine for AgentX Serve.
 *
 * Provides model discovery and server integration points. Model execution remains disabled until a genuine GGUF runtime is connected.
 *
 * Supports both pre-configured curated edge models and user-chosen models stored in phone storage (.gguf).
 */
class LocalModelServerEngine(
    val thermalManager: ThermalBatteryManager? = null
) {

    private val mutex = Mutex()
    private val isCancelled = AtomicBoolean(false)

    val curatedCatalog = listOf(
        LocalServerModel(
            id = "llama-3.2-3b",
            name = "Llama-3.2-3B-Instruct",
            architecture = "Llama",
            parameterCount = "3.2B",
            quantization = "Q4_K_M",
            contextWindow = 4096,
            memoryFootprintMb = 0L,
            description = "Meta's flagship mobile conversational & reasoning model. Excellent all-rounder for general assistance and tool calling.",
            isLoaded = false,
            isServing = false
        ),
        LocalServerModel(
            id = "deepseek-r1-1.5b",
            name = "DeepSeek-R1-Distill-1.5B",
            architecture = "Qwen2",
            parameterCount = "1.5B",
            quantization = "Q4_K_M",
            contextWindow = 8192,
            memoryFootprintMb = 0L,
            description = "High-efficiency distilled reasoning model specialized in multi-step deduction, math, and code logic.",
            isLoaded = false,
            isServing = false
        ),
        LocalServerModel(
            id = "qwen-2.5-coder-3b",
            name = "Qwen-2.5-Coder-3B",
            architecture = "Qwen2",
            parameterCount = "3.1B",
            quantization = "Q4_K_M",
            contextWindow = 4096,
            memoryFootprintMb = 0L,
            description = "Alibaba's specialized code synthesis engine for Kotlin, Python, TypeScript, and software architecture.",
            isLoaded = false,
            isServing = false
        ),
        LocalServerModel(
            id = "biomistral-7b",
            name = "BioMistral-7B",
            architecture = "Mistral",
            parameterCount = "7.2B",
            quantization = "Q4_K_M",
            contextWindow = 4096,
            memoryFootprintMb = 0L,
            description = "Biomedical foundation model fine-tuned on PubMed Central and clinical practice guidelines.",
            isLoaded = false,
            isServing = false
        ),
        LocalServerModel(
            id = "phi-3.5-mini",
            name = "Phi-3.5-Mini-Instruct",
            architecture = "Phi-3",
            parameterCount = "3.8B",
            quantization = "Q4_K_M",
            contextWindow = 4096,
            memoryFootprintMb = 0L,
            description = "Microsoft's state-of-the-art compact reasoning model with high benchmark scores across math and logic.",
            isLoaded = false,
            isServing = false
        )
    )

    private val _storageModels = mutableListOf<LocalServerModel>()
    val storageModels: List<LocalServerModel> get() = _storageModels.toList()

    val allAvailableModels: List<LocalServerModel> get() = _storageModels + curatedCatalog

    private var _activeModel = curatedCatalog.first().copy(isLoaded = false, isServing = false)
    val activeModel: LocalServerModel get() = _activeModel

    private var _status = LocalModelStatus.NOT_LOADED
    val status: LocalModelStatus get() = _status

    fun registerStorageModel(
        name: String,
        filePath: String,
        fileSizeBytes: Long
    ): LocalServerModel {
        val sanitizedId = "storage_" + name.replace("[^a-zA-Z0-9._-]".toRegex(), "_")
        val existing = _storageModels.find { it.id == sanitizedId || it.filePath == filePath }
        if (existing != null) {
            return existing
        }

        val arch = detectArchitecture(name)
        val params = detectParameters(name, fileSizeBytes)
        val quant = detectQuantization(name)
        val ramMb = computeLayerStreamingRamMb(fileSizeBytes)

        val newModel = LocalServerModel(
            id = sanitizedId,
            name = name.removeSuffix(".gguf"),
            architecture = arch,
            parameterCount = params,
            quantization = quant,
            contextWindow = 4096,
            memoryFootprintMb = ramMb,
            description = "Discovered model file. Runtime compatibility and memory requirements have not yet been validated.",
            isLoaded = false,
            isServing = false,
            filePath = filePath,
            fileSizeBytes = fileSizeBytes,
            isCustomStorageModel = true
        )
        _storageModels.add(0, newModel)
        return newModel
    }

    fun removeStorageModel(modelId: String): Boolean {
        val removed = _storageModels.removeAll { it.id == modelId }
        if (_activeModel.id == modelId) {
            _activeModel = curatedCatalog.first().copy(isLoaded = false, isServing = false)
        }
        return removed
    }

    fun scanStorageForModels(context: Context): List<LocalServerModel> {
        val discovered = mutableListOf<LocalServerModel>()
        val candidateDirs = mutableListOf<File>()

        try {
            val appModels = File(context.getExternalFilesDir(null), "models")
            if (appModels.exists() && appModels.isDirectory) {
                candidateDirs.add(appModels)
            }
        } catch (_: Exception) {}

        try {
            val downloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            if (downloads != null && downloads.exists() && downloads.isDirectory) {
                candidateDirs.add(downloads)
            }
        } catch (_: Exception) {}

        try {
            val docs = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
            if (docs != null && docs.exists() && docs.isDirectory) {
                candidateDirs.add(docs)
            }
        } catch (_: Exception) {}

        for (dir in candidateDirs) {
            try {
                dir.walkTopDown().maxDepth(2).forEach { file ->
                    if (file.isFile && file.name.endsWith(".gguf", ignoreCase = true)) {
                        val registered = registerStorageModel(file.name, file.absolutePath, file.length())
                        if (!discovered.any { it.id == registered.id }) {
                            discovered.add(registered)
                        }
                    }
                }
            } catch (_: Exception) {}
        }

        return discovered
    }

    /**
     * Model catalog entries are not loaded weights. Until the shared native runtime
     * is connected, refuse to mark any model as loaded or serving.
     */
    suspend fun loadModel(modelId: String): Boolean = withContext(Dispatchers.IO) {
        false
    }

    suspend fun unloadModel() = withContext(Dispatchers.IO) {
        mutex.withLock {
            isCancelled.set(true)
            _activeModel = _activeModel.copy(isLoaded = false, isServing = false)
            _status = LocalModelStatus.NOT_LOADED
        }
    }

    fun generateCompletionStream(
        prompt: String,
        systemPrompt: String? = null,
        temperature: Float = 0.7f,
        maxTokens: Int = 1024
    ): Flow<String> = flow {
        throw IllegalStateException(
            "Local model inference is unavailable: AgentX Serve is not connected to a real GGUF runtime. No synthetic output was generated."
        )
    }

    suspend fun generateCompletionSync(
        prompt: String,
        systemPrompt: String? = null,
        temperature: Float = 0.7f,
        maxTokens: Int = 1024
    ): String = withContext(Dispatchers.IO) {
        val sb = java.lang.StringBuilder()
        generateCompletionStream(prompt, systemPrompt, temperature, maxTokens).collect { token ->
            sb.append(token)
        }
        sb.toString()
    }

    companion object {
        fun detectArchitecture(fileName: String): String {
            val lower = fileName.lowercase()
            return when {
                lower.contains("llama") -> "Llama"
                lower.contains("mistral") -> "Mistral"
                lower.contains("deepseek") -> "DeepSeek"
                lower.contains("qwen") -> "Qwen"
                lower.contains("phi") -> "Phi"
                lower.contains("gemma") -> "Gemma"
                lower.contains("biomistral") -> "BioMistral"
                lower.contains("starcoder") -> "StarCoder"
                else -> "Transformer"
            }
        }

        fun detectParameters(fileName: String, fileSizeBytes: Long): String {
            val lower = fileName.lowercase()
            val match = Regex("(\\d+(\\.\\d+)?)[bB]").find(lower)
            if (match != null) {
                return match.groupValues[1] + "B"
            }
            return "Unknown"
        }

        fun detectQuantization(fileName: String): String {
            val upper = fileName.uppercase()
            val quantRegex = Regex("Q\\d+_[A-Z0-9_]+")
            val match = quantRegex.find(upper)
            return match?.value ?: if (upper.contains("FP16")) "FP16" else "Unknown"
        }

        fun computeLayerStreamingRamMb(fileSizeBytes: Long): Long = 0L // Unknown until a real runtime measures allocation.

    }
}
