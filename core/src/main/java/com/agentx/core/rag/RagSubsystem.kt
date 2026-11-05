package com.agentx.core.rag

import kotlinx.serialization.Serializable
import java.util.UUID

/**
 * Dedicated Document Intelligence & RAG Subsystem contracts specified in Section F of Master Architecture.
 */

@Serializable
enum class SupportedDocumentType(val extension: String, val mimeType: String) {
    PDF(".pdf", "application/pdf"),
    DOCX(".docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document"),
    TXT(".txt", "text/plain"),
    MARKDOWN(".md", "text/markdown"),
    HTML(".html", "text/html"),
    CSV(".csv", "text/csv"),
    XLSX(".xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
}

@Serializable
data class DocumentMetadata(
    val id: String = UUID.randomUUID().toString(),
    val fileName: String,
    val fileSizeBytes: Long,
    val type: SupportedDocumentType,
    val sha256Hash: String,
    val importedAtMs: Long = System.currentTimeMillis(),
    val pageCount: Int? = null,
    val customProperties: Map<String, String> = emptyMap()
)

@Serializable
data class DocumentChunk(
    val id: String = UUID.randomUUID().toString(),
    val documentId: String,
    val chunkIndex: Int,
    val text: String,
    val tokenCount: Int,
    val pageNumber: Int? = null,
    val embedding: List<Float>? = null
)

@Serializable
data class DocumentCitation(
    val documentId: String,
    val fileName: String,
    val chunkId: String,
    val textSnippet: String,
    val pageNumber: Int? = null,
    val similarityScore: Float = 0.0f
)

interface DocumentParser {
    val supportedType: SupportedDocumentType
    suspend fun parse(inputStream: java.io.InputStream, metadata: DocumentMetadata): String
}

interface DocumentChunker {
    fun chunk(text: String, documentId: String, targetChunkSize: Int = 512, overlap: Int = 64): List<DocumentChunk>
}

class SlidingWindowChunker : DocumentChunker {
    override fun chunk(
        text: String,
        documentId: String,
        targetChunkSize: Int,
        overlap: Int
    ): List<DocumentChunk> {
        val words = text.split(Regex("\\s+")).filter { it.isNotBlank() }
        if (words.isEmpty()) return emptyList()

        val chunks = mutableListOf<DocumentChunk>()
        var start = 0
        var chunkIndex = 0

        while (start < words.size) {
            val end = (start + targetChunkSize).coerceAtMost(words.size)
            val chunkWords = words.subList(start, end)
            val chunkText = chunkWords.joinToString(" ")

            chunks.add(
                DocumentChunk(
                    documentId = documentId,
                    chunkIndex = chunkIndex++,
                    text = chunkText,
                    tokenCount = (chunkWords.size * 1.3).toInt() // Rough token estimation
                )
            )

            if (end >= words.size) break
            start += (targetChunkSize - overlap).coerceAtLeast(1)
        }

        return chunks
    }
}

interface VectorStore {
    suspend fun insertChunks(chunks: List<DocumentChunk>)
    suspend fun searchSimilar(queryEmbedding: FloatArray, topK: Int = 5, threshold: Float = 0.65f): List<DocumentCitation>
    suspend fun deleteDocument(documentId: String)
    suspend fun getChunkCount(): Int
}
