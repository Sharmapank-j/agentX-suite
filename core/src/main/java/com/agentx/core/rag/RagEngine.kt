package com.agentx.core.rag

import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import kotlin.math.sqrt

/**
 * Concrete implementations for Document Intelligence & RAG Subsystem
 * specified in Section F of the Master Architecture.
 */

class PlainTextDocumentParser(
    override val supportedType: SupportedDocumentType
) : DocumentParser {
    override suspend fun parse(inputStream: InputStream, metadata: DocumentMetadata): String {
        return BufferedReader(InputStreamReader(inputStream, Charsets.UTF_8)).use { reader ->
            val content = reader.readText()
            when (metadata.type) {
                SupportedDocumentType.HTML -> stripHtmlTags(content)
                else -> content
            }
        }
    }

    private fun stripHtmlTags(html: String): String {
        return html
            .replace(Regex("<script[^>]*>[\\s\\S]*?</script>", RegexOption.IGNORE_CASE), "")
            .replace(Regex("<style[^>]*>[\\s\\S]*?</style>", RegexOption.IGNORE_CASE), "")
            .replace(Regex("<[^>]+>"), " ")
            .replace(Regex("&nbsp;"), " ")
            .replace(Regex("&amp;"), "&")
            .replace(Regex("&lt;"), "<")
            .replace(Regex("&gt;"), ">")
            .replace(Regex("\\s+"), " ")
            .trim()
    }
}

/**
 * High-performance, offline on-device Vector & Lexical Store.
 * Supports exact cosine similarity search over float embedding vectors,
 * with automatic fallback to BM25-style lexical scoring if query or chunk has no embeddings.
 */
class InMemoryVectorStore(
    private val maxCapacity: Int = 2000
) : VectorStore {

    private val lock = Any()
    private val chunkMap = LinkedHashMap<String, DocumentChunk>()
    private val docIdToChunkIds = mutableMapOf<String, MutableSet<String>>()

    override suspend fun insertChunks(chunks: List<DocumentChunk>): Unit = synchronized(lock) {
        for (chunk in chunks) {
            chunkMap[chunk.id] = chunk
            docIdToChunkIds.getOrPut(chunk.documentId) { mutableSetOf() }.add(chunk.id)
        }
        // Enforce max capacity
        if (chunkMap.size > maxCapacity) {
            val excess = chunkMap.size - maxCapacity
            val oldestKeys = chunkMap.keys.take(excess).toList()
            for (key in oldestKeys) {
                val removed = chunkMap.remove(key)
                if (removed != null) {
                    docIdToChunkIds[removed.documentId]?.remove(removed.id)
                }
            }
        }
    }

    override suspend fun searchSimilar(
        queryEmbedding: FloatArray,
        topK: Int,
        threshold: Float
    ): List<DocumentCitation> = synchronized(lock) {
        if (chunkMap.isEmpty()) return emptyList()

        val results = mutableListOf<Pair<DocumentChunk, Float>>()

        for (chunk in chunkMap.values) {
            val chunkEmb = chunk.embedding
            if (chunkEmb != null && chunkEmb.size == queryEmbedding.size) {
                val similarity = cosineSimilarity(queryEmbedding, chunkEmb)
                if (similarity >= threshold) {
                    results.add(chunk to similarity)
                }
            }
        }

        return results
            .sortedByDescending { it.second }
            .take(topK)
            .map { (chunk, score) ->
                DocumentCitation(
                    documentId = chunk.documentId,
                    fileName = chunk.documentId,
                    chunkId = chunk.id,
                    textSnippet = chunk.text.take(280),
                    pageNumber = chunk.pageNumber,
                    similarityScore = score
                )
            }
    }

    /**
     * Lexical keyword search fallback when vector embeddings are not yet generated.
     */
    fun searchLexical(
        query: String,
        topK: Int = 5,
        minScore: Float = 0.1f
    ): List<DocumentCitation> = synchronized(lock) {
        if (chunkMap.isEmpty()) return emptyList()

        val queryTokens = query.lowercase().split(Regex("[^a-zA-Z0-9]+")).filter { it.length > 2 }
        if (queryTokens.isEmpty()) return emptyList()

        val results = mutableListOf<Pair<DocumentChunk, Float>>()

        for (chunk in chunkMap.values) {
            val textLower = chunk.text.lowercase()
            var matches = 0
            for (token in queryTokens) {
                if (textLower.contains(token)) {
                    matches++
                }
            }
            if (matches > 0) {
                val score = matches.toFloat() / queryTokens.size
                if (score >= minScore) {
                    results.add(chunk to score)
                }
            }
        }

        return results
            .sortedByDescending { it.second }
            .take(topK)
            .map { (chunk, score) ->
                DocumentCitation(
                    documentId = chunk.documentId,
                    fileName = chunk.documentId,
                    chunkId = chunk.id,
                    textSnippet = chunk.text.take(280),
                    pageNumber = chunk.pageNumber,
                    similarityScore = score
                )
            }
    }

    override suspend fun deleteDocument(documentId: String): Unit = synchronized(lock) {
        val chunkIds = docIdToChunkIds.remove(documentId) ?: return
        for (id in chunkIds) {
            chunkMap.remove(id)
        }
    }

    override suspend fun getChunkCount(): Int = synchronized(lock) {
        chunkMap.size
    }

    private fun cosineSimilarity(v1: FloatArray, v2: List<Float>): Float {
        var dot = 0.0f
        var normA = 0.0f
        var normB = 0.0f
        for (i in v1.indices) {
            val a = v1[i]
            val b = v2[i]
            dot += a * b
            normA += a * a
            normB += b * b
        }
        val denom = sqrt(normA) * sqrt(normB)
        return if (denom > 1e-6f) (dot / denom).coerceIn(-1.0f, 1.0f) else 0.0f
    }
}

/**
 * End-to-end RAG orchestrator for document parsing, chunking, and contextual retrieval.
 */
class RagEngine(
    private val vectorStore: InMemoryVectorStore = InMemoryVectorStore(),
    private val chunker: DocumentChunker = SlidingWindowChunker()
) {
    private val parsers = mutableMapOf<SupportedDocumentType, DocumentParser>()

    init {
        // Register default parsers
        registerParser(PlainTextDocumentParser(SupportedDocumentType.TXT))
        registerParser(PlainTextDocumentParser(SupportedDocumentType.MARKDOWN))
        registerParser(PlainTextDocumentParser(SupportedDocumentType.CSV))
        registerParser(PlainTextDocumentParser(SupportedDocumentType.HTML))
    }

    fun registerParser(parser: DocumentParser) {
        parsers[parser.supportedType] = parser
    }

    suspend fun ingestDocument(
        inputStream: InputStream,
        metadata: DocumentMetadata,
        chunkSize: Int = 256,
        overlap: Int = 32
    ): List<DocumentChunk> {
        val parser = parsers[metadata.type]
            ?: PlainTextDocumentParser(metadata.type) // fallback to text parser

        val fullText = parser.parse(inputStream, metadata)
        val chunks = chunker.chunk(fullText, metadata.id, chunkSize, overlap)
        vectorStore.insertChunks(chunks)
        return chunks
    }

    suspend fun ingestText(
        text: String,
        documentId: String,
        chunkSize: Int = 256,
        overlap: Int = 32
    ): List<DocumentChunk> {
        val chunks = chunker.chunk(text, documentId, chunkSize, overlap)
        vectorStore.insertChunks(chunks)
        return chunks
    }

    suspend fun retrieveRelevant(
        query: String,
        queryEmbedding: FloatArray? = null,
        topK: Int = 3
    ): List<DocumentCitation> {
        return if (queryEmbedding != null) {
            val semanticResults = vectorStore.searchSimilar(queryEmbedding, topK, 0.45f)
            if (semanticResults.isNotEmpty()) semanticResults else vectorStore.searchLexical(query, topK)
        } else {
            vectorStore.searchLexical(query, topK)
        }
    }

    /**
     * Builds an augmented system context block containing retrieved evidence and citations.
     */
    fun formatCitationsForPrompt(citations: List<DocumentCitation>): String {
        if (citations.isEmpty()) return ""
        val sb = StringBuilder("\n--- RETRIEVED KNOWLEDGE BASE CONTEXT ---\n")
        citations.forEachIndexed { index, citation ->
            sb.append("[${index + 1}] Source: ${citation.fileName} (Chunk: ${citation.chunkId.take(6)}, Relevance: ${(citation.similarityScore * 100).toInt()}%)\n")
            sb.append("\"${citation.textSnippet.trim()}\"\n\n")
        }
        sb.append("--- END RETRIEVED CONTEXT ---\n")
        sb.append("Instruct the assistant to cite the above references when answering.")
        return sb.toString()
    }
}
