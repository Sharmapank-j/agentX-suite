package com.agentx.core.inference

import android.content.Context
import com.agentx.data.local.ModelFileManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

data class CuratedModel(
    val id: String,
    val name: String,
    val description: String,
    val repoId: String,
    val filename: String,
    val downloadUrl: String,
    val estimatedSizeBytes: Long,
    val quantization: String,
    val ramRequirement: String,
    val domain: String
)

typealias CuratedMedicalModel = CuratedModel

sealed interface DownloadProgress {
    data class Downloading(
        val bytesDownloaded: Long,
        val totalBytes: Long,
        val fraction: Float,
        val speedBytesPerSec: Long,
        val etaSeconds: Long
    ) : DownloadProgress

    data class Success(val file: File, val metadata: GgufMetadata) : DownloadProgress
    data class Error(val message: String, val throwable: Throwable? = null) : DownloadProgress
}

class HuggingFaceDownloader(
    private val context: Context,
    private val modelFileManager: ModelFileManager
) {

    companion object {
        val CURATED_MODELS = listOf(
            CuratedModel(
                id = "curated_llama32_3b_q4km",
                name = "Llama 3.2 3B Instruct (Q4_K_M)",
                description = "Ultra-compact high-performance edge assistant model for rapid mobile inference and low RAM footprint.",
                repoId = "bartowski/Llama-3.2-3B-Instruct-GGUF",
                filename = "Llama-3.2-3B-Instruct-Q4_K_M.gguf",
                downloadUrl = "https://huggingface.co/bartowski/Llama-3.2-3B-Instruct-GGUF/resolve/main/Llama-3.2-3B-Instruct-Q4_K_M.gguf",
                estimatedSizeBytes = 2_020_000_000L,
                quantization = "Q4_K_M",
                ramRequirement = "3.5 GB RAM",
                domain = "General Assistant & Edge Conversational"
            ),
            CuratedModel(
                id = "curated_qwen25_coder_7b_q4km",
                name = "Qwen 2.5 Coder 7B (Q4_K_M)",
                description = "Flagship open-source code generation, systems architecture, and algorithmic debugging LLM.",
                repoId = "Qwen/Qwen2.5-Coder-7B-Instruct-GGUF",
                filename = "qwen2.5-coder-7b-instruct-q4_k_m.gguf",
                downloadUrl = "https://huggingface.co/Qwen/Qwen2.5-Coder-7B-Instruct-GGUF/resolve/main/qwen2.5-coder-7b-instruct-q4_k_m.gguf",
                estimatedSizeBytes = 4_680_000_000L,
                quantization = "Q4_K_M",
                ramRequirement = "6 GB RAM",
                domain = "Software Engineering & Architecture"
            ),
            CuratedModel(
                id = "curated_deepseek_r1_1_5b_q4km",
                name = "DeepSeek R1 Distill Qwen 1.5B (Q4_K_M)",
                description = "Lightweight reasoning and chain-of-thought distillation model with high step-by-step logic accuracy.",
                repoId = "bartowski/DeepSeek-R1-Distill-Qwen-1.5B-GGUF",
                filename = "DeepSeek-R1-Distill-Qwen-1.5B-Q4_K_M.gguf",
                downloadUrl = "https://huggingface.co/bartowski/DeepSeek-R1-Distill-Qwen-1.5B-GGUF/resolve/main/DeepSeek-R1-Distill-Qwen-1.5B-Q4_K_M.gguf",
                estimatedSizeBytes = 1_120_000_000L,
                quantization = "Q4_K_M",
                ramRequirement = "2.2 GB RAM",
                domain = "Deep Reasoning & Mathematics"
            ),
            CuratedModel(
                id = "curated_phi35_mini_q4km",
                name = "Phi-3.5 Mini 3.8B Instruct (Q4_K_M)",
                description = "Advanced small language model by Microsoft offering state-of-the-art multilingual and multi-turn reasoning.",
                repoId = "bartowski/Phi-3.5-mini-instruct-GGUF",
                filename = "Phi-3.5-mini-instruct-Q4_K_M.gguf",
                downloadUrl = "https://huggingface.co/bartowski/Phi-3.5-mini-instruct-GGUF/resolve/main/Phi-3.5-mini-instruct-Q4_K_M.gguf",
                estimatedSizeBytes = 2_390_000_000L,
                quantization = "Q4_K_M",
                ramRequirement = "4 GB RAM",
                domain = "Reasoning & Edge Analysis"
            )
        )

        fun sanitizeHuggingFaceUrl(rawUrl: String): String {
            var url = rawUrl.trim()
            if (url.contains("huggingface.co") && url.contains("/blob/")) {
                url = url.replace("/blob/", "/resolve/")
            }
            return url
        }

        fun extractFilenameFromUrl(urlStr: String): String {
            val path = try {
                URL(urlStr).path
            } catch (_: Exception) {
                urlStr
            }
            val name = path.substringAfterLast("/")
            return if (name.endsWith(".gguf", ignoreCase = true)) name else "$name.gguf"
        }
    }

    fun downloadModel(
        rawUrl: String,
        targetFilename: String? = null
    ): Flow<DownloadProgress> = flow {
        val directUrl = sanitizeHuggingFaceUrl(rawUrl)
        val finalFilename = targetFilename ?: extractFilenameFromUrl(directUrl)

        val modelsDir = File(context.getExternalFilesDir(null), "models")
        if (!modelsDir.exists()) modelsDir.mkdirs()

        val tempFile = File(modelsDir, "${finalFilename}.download")
        val destinationFile = File(modelsDir, finalFilename)

        try {
            val url = URL(directUrl)
            val connection = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 15000
                readTimeout = 30000
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", "AgentX-Android/1.0")
            }

            var responseCode = connection.responseCode
            // Follow up to 5 redirects if needed (common with Hugging Face LFS CDN)
            var redirects = 0
            var currentConnection = connection
            while ((responseCode == HttpURLConnection.HTTP_MOVED_TEMP ||
                    responseCode == HttpURLConnection.HTTP_MOVED_PERM ||
                    responseCode == 307 || responseCode == 308) && redirects < 5) {
                val newUrl = currentConnection.getHeaderField("Location")
                currentConnection.disconnect()
                currentConnection = (URL(newUrl).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 15000
                    readTimeout = 30000
                    setRequestProperty("User-Agent", "AgentX-Android/1.0")
                }
                responseCode = currentConnection.responseCode
                redirects++
            }

            if (responseCode !in 200..299) {
                emit(DownloadProgress.Error("HTTP Error: $responseCode ${currentConnection.responseMessage}"))
                currentConnection.disconnect()
                return@flow
            }

            val contentLength = currentConnection.contentLengthLong
            val freeSpace = modelFileManager.getAvailableStorageBytes()
            if (contentLength > 0 && freeSpace < contentLength + (150 * 1024 * 1024)) {
                emit(DownloadProgress.Error("Insufficient storage space. Need ${contentLength / (1024 * 1024)}MB, free space is ${freeSpace / (1024 * 1024)}MB"))
                currentConnection.disconnect()
                return@flow
            }

            var bytesReadTotal = 0L
            val startTime = System.currentTimeMillis()
            var lastUpdateTime = startTime
            var bytesAtLastUpdate = 0L
            var speed = 0L

            currentConnection.inputStream.use { input ->
                FileOutputStream(tempFile).use { output ->
                    val buffer = ByteArray(1024 * 128)
                    var count: Int

                    while (input.read(buffer).also { count = it } != -1) {
                        output.write(buffer, 0, count)
                        bytesReadTotal += count

                        val now = System.currentTimeMillis()
                        if (now - lastUpdateTime >= 500) {
                            val timeDeltaSec = (now - lastUpdateTime) / 1000.0
                            val bytesDelta = bytesReadTotal - bytesAtLastUpdate
                            speed = if (timeDeltaSec > 0) (bytesDelta / timeDeltaSec).toLong() else speed

                            val fraction = if (contentLength > 0) (bytesReadTotal.toFloat() / contentLength) else 0f
                            val remainingBytes = if (contentLength > 0) (contentLength - bytesReadTotal) else 0L
                            val eta = if (speed > 0) (remainingBytes / speed) else 0L

                            emit(DownloadProgress.Downloading(bytesReadTotal, contentLength, fraction, speed, eta))
                            lastUpdateTime = now
                            bytesAtLastUpdate = bytesReadTotal
                        }
                    }
                    output.flush()
                }
            }

            currentConnection.disconnect()

            // Validate GGUF header before finalizing
            val metadata = tempFile.inputStream().use { stream ->
                GgufMetadataParser.parse(stream)
            }

            if (!metadata.isValid) {
                tempFile.delete()
                emit(DownloadProgress.Error("Downloaded file is not a valid GGUF file: ${metadata.errorMessage}"))
                return@flow
            }

            if (destinationFile.exists()) destinationFile.delete()
            tempFile.renameTo(destinationFile)

            emit(DownloadProgress.Success(destinationFile, metadata))
        } catch (e: Exception) {
            if (tempFile.exists()) tempFile.delete()
            emit(DownloadProgress.Error("Download failed: ${e.message}", e))
        }
    }.flowOn(Dispatchers.IO)
}
