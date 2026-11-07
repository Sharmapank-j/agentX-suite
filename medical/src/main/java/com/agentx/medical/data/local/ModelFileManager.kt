package com.agentx.medical.data.local

import android.content.Context
import android.content.Intent
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

sealed interface StagingProgress {
    data class Progress(val bytesCopied: Long, val totalBytes: Long, val percentage: Float) : StagingProgress
    data class Success(val stagedFile: File) : StagingProgress
    data class Error(val message: String, val throwable: Throwable? = null) : StagingProgress
}

class ModelFileManager(private val context: Context) {

    private val modelsDir: File
        get() {
            val dir = File(context.getExternalFilesDir(null), "models")
            if (!dir.exists()) {
                dir.mkdirs()
            }
            return dir
        }

    fun getAvailableStorageBytes(): Long {
        return try {
            modelsDir.freeSpace
        } catch (_: Exception) {
            0L
        }
    }

    fun getTotalStorageUsedBytes(): Long {
        return try {
            modelsDir.listFiles()?.sumOf { it.length() } ?: 0L
        } catch (_: Exception) {
            0L
        }
    }

    fun takePersistablePermission(uri: Uri) {
        try {
            val takeFlags: Int = Intent.FLAG_GRANT_READ_URI_PERMISSION
            context.contentResolver.takePersistableUriPermission(uri, takeFlags)
        } catch (_: Exception) {
            // Some file providers or virtual files may not support persistable permissions
        }
    }

    fun stageModelFile(
        sourceUri: Uri,
        modelId: String,
        fileName: String,
        totalBytes: Long
    ): Flow<StagingProgress> = flow {
        val sanitizedName = fileName.replace("[^a-zA-Z0-9._-]".toRegex(), "_")
        val destinationFile = File(modelsDir, "${modelId}_$sanitizedName")

        try {
            context.contentResolver.openInputStream(sourceUri)?.use { inputStream ->
                FileOutputStream(destinationFile).use { outputStream ->
                    val buffer = ByteArray(1024 * 256) // 256KB buffer
                    var bytesCopied = 0L
                    var lastEmittedBytes = 0L
                    var lastEmittedTime = 0L
                    var read: Int

                    while (inputStream.read(buffer).also { read = it } != -1) {
                        outputStream.write(buffer, 0, read)
                        bytesCopied += read
                        val now = System.currentTimeMillis()
                        // Throttle emissions to at most every 250ms or every 4MB to prevent UI thread lockup
                        if (bytesCopied - lastEmittedBytes >= 4 * 1024 * 1024 || (now - lastEmittedTime) >= 250) {
                            val pct = if (totalBytes > 0) (bytesCopied.toFloat() / totalBytes) else 0f
                            emit(StagingProgress.Progress(bytesCopied, totalBytes, pct))
                            lastEmittedBytes = bytesCopied
                            lastEmittedTime = now
                        }
                    }
                    outputStream.flush()
                    // Final 100% emission
                    emit(StagingProgress.Progress(bytesCopied, totalBytes, 1.0f))
                }
            } ?: run {
                emit(StagingProgress.Error("Failed to open input stream for source URI: $sourceUri"))
                return@flow
            }

            emit(StagingProgress.Success(destinationFile))
        } catch (e: Exception) {
            if (destinationFile.exists()) {
                destinationFile.delete()
            }
            emit(StagingProgress.Error("File staging error: ${e.message}", e))
        }
    }.flowOn(Dispatchers.IO)

    suspend fun deleteStagedFile(filePath: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val file = File(filePath)
            if (file.exists() && file.canonicalPath.startsWith(modelsDir.canonicalPath)) {
                file.delete()
            } else {
                false
            }
        } catch (_: Exception) {
            false
        }
    }
}
