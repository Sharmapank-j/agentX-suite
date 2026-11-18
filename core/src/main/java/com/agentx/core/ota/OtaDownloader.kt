package com.agentx.core.ota

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import kotlin.coroutines.coroutineContext

/**
 * Handles streaming HTTP/HTTPS downloads with real-time speed, bytes, and percentage progress emission.
 */
class OtaDownloader(
    private val connectTimeoutMs: Int = 15_000,
    private val readTimeoutMs: Int = 30_000
) {

    suspend fun downloadToFile(
        urlStr: String,
        targetFile: File,
        expectedSizeBytes: Long = 0L,
        onProgress: (OtaDownloadProgress) -> Unit = {}
    ): Result<File> = withContext(Dispatchers.IO) {
        val tempFile = File(targetFile.parentFile, "${targetFile.name}.tmp")
        try {
            targetFile.parentFile?.mkdirs()
            if (tempFile.exists()) {
                tempFile.delete()
            }

            val url = URL(urlStr)
            val conn = url.openConnection().apply {
                connectTimeout = connectTimeoutMs
                readTimeout = readTimeoutMs
                if (this is HttpURLConnection) {
                    requestMethod = "GET"
                    setRequestProperty("User-Agent", "AgentX-OTA-Client/1.0")
                    setRequestProperty("Accept-Encoding", "identity")
                }
            }

            if (conn is HttpURLConnection) {
                val responseCode = conn.responseCode
                if (responseCode !in 200..299) {
                    return@withContext Result.failure(
                        IllegalStateException("HTTP $responseCode from download endpoint: $urlStr")
                    )
                }
            }

            val contentLength = conn.contentLengthLong.takeIf { it > 0 } ?: expectedSizeBytes
            var totalBytesRead = 0L
            val startTime = System.currentTimeMillis()
            var lastProgressTime = startTime

            conn.inputStream.use { input ->
                FileOutputStream(tempFile).use { output ->
                    val buffer = ByteArray(8192)
                    while (coroutineContext.isActive) {
                        val bytesRead = input.read(buffer)
                        if (bytesRead == -1) break
                        output.write(buffer, 0, bytesRead)
                        totalBytesRead += bytesRead

                        val now = System.currentTimeMillis()
                        if (now - lastProgressTime >= 100L || totalBytesRead == contentLength) {
                            val elapsedSec = (now - startTime) / 1000.0
                            val speedBps = if (elapsedSec > 0) (totalBytesRead / elapsedSec).toLong() else 0L
                            val progressPct = if (contentLength > 0) {
                                (totalBytesRead.toFloat() / contentLength.toFloat()).coerceIn(0f, 1f)
                            } else {
                                0.5f
                            }
                            onProgress(
                                OtaDownloadProgress(
                                    bytesDownloaded = totalBytesRead,
                                    totalBytes = contentLength,
                                    progressPct = progressPct,
                                    speedBps = speedBps
                                )
                            )
                            lastProgressTime = now
                        }
                    }
                }
            }

            if (!coroutineContext.isActive) {
                tempFile.delete()
                return@withContext Result.failure(IllegalStateException("Download cancelled"))
            }

            if (targetFile.exists()) {
                targetFile.delete()
            }

            if (tempFile.renameTo(targetFile)) {
                Result.success(targetFile)
            } else {
                tempFile.copyTo(targetFile, overwrite = true)
                tempFile.delete()
                Result.success(targetFile)
            }
        } catch (e: Exception) {
            try { tempFile.delete() } catch (_: Exception) {}
            Result.failure(e)
        }
    }
}
