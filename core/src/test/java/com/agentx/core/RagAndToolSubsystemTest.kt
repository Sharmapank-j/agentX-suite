package com.agentx.core

import com.agentx.core.agent.tools.CalculatorTool
import com.agentx.core.agent.tools.DeviceTelemetryTool
import com.agentx.core.agent.tools.LocalMcpClient
import com.agentx.core.agent.tools.SystemTimeTool
import com.agentx.core.rag.DocumentChunk
import com.agentx.core.rag.DocumentMetadata
import com.agentx.core.rag.InMemoryVectorStore
import com.agentx.core.rag.PlainTextDocumentParser
import com.agentx.core.rag.RagEngine
import com.agentx.core.rag.SlidingWindowChunker
import com.agentx.core.rag.SupportedDocumentType
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream

class RagAndToolSubsystemTest {

    @Test
    fun testPlainTextAndHtmlParser() = runTest {
        val parserHtml = PlainTextDocumentParser(SupportedDocumentType.HTML)
        val htmlContent = "<html><body><h1>Guideline Title</h1><p>Patient presenting with acute chest pain.</p></body></html>"
        val input = ByteArrayInputStream(htmlContent.toByteArray(Charsets.UTF_8))
        val metadata = DocumentMetadata(
            fileName = "guideline.html",
            fileSizeBytes = htmlContent.length.toLong(),
            type = SupportedDocumentType.HTML,
            sha256Hash = "abc123"
        )
        val text = parserHtml.parse(input, metadata)
        assertTrue(text.contains("Guideline Title"))
        assertTrue(text.contains("acute chest pain"))
        assertFalse(text.contains("<html>"))
        assertFalse(text.contains("<body>"))
    }

    @Test
    fun testSlidingWindowChunker() {
        val chunker = SlidingWindowChunker()
        val text = (1..100).joinToString(" ") { "word$it" }
        val chunks = chunker.chunk(text, "doc_1", targetChunkSize = 30, overlap = 5)

        assertTrue(chunks.isNotEmpty())
        assertEquals("doc_1", chunks[0].documentId)
        assertTrue(chunks[0].tokenCount > 0)
        // With targetChunkSize=30, words are partitioned across multiple chunks
        assertTrue(chunks.size >= 4)
    }

    @Test
    fun testVectorStoreCosineSimilarity() = runTest {
        val store = InMemoryVectorStore()

        // 3D embedding vectors for demonstration
        val chunkA = DocumentChunk(
            id = "c1",
            documentId = "d1",
            chunkIndex = 0,
            text = "Acute coronary syndrome management and ECG interpretation.",
            tokenCount = 10,
            embedding = listOf(1.0f, 0.0f, 0.0f)
        )
        val chunkB = DocumentChunk(
            id = "c2",
            documentId = "d1",
            chunkIndex = 1,
            text = "Deep vein thrombosis Wells criteria score interpretation.",
            tokenCount = 10,
            embedding = listOf(0.0f, 1.0f, 0.0f)
        )

        store.insertChunks(listOf(chunkA, chunkB))

        // Query vector aligned with chunk A
        val queryEmb = floatArrayOf(0.95f, 0.05f, 0.0f)
        val results = store.searchSimilar(queryEmb, topK = 1, threshold = 0.5f)

        assertEquals(1, results.size)
        assertEquals("c1", results[0].chunkId)
        assertTrue(results[0].similarityScore > 0.9f)
    }

    @Test
    fun testRagEngineEndToEndPipeline() = runTest {
        val engine = RagEngine()

        val sampleArticle = """
            Hypertension management guidelines recommend lifestyle modifications as the initial step for stage 1 hypertension.
            Pharmacological therapy with ACE inhibitors, ARBs, or thiazide diuretics should be initiated if blood pressure remains elevated.
            Target blood pressure is typically below 130/80 mmHg for high-risk patients.
        """.trimIndent()

        engine.ingestText(sampleArticle, "htn_guide_2026")

        val citations = engine.retrieveRelevant("ACE inhibitors pharmacological therapy blood pressure")
        assertTrue(citations.isNotEmpty())
        assertTrue(citations[0].textSnippet.contains("pharmacological", ignoreCase = true))

        val promptContext = engine.formatCitationsForPrompt(citations)
        assertTrue(promptContext.contains("RETRIEVED KNOWLEDGE BASE CONTEXT"))
        assertTrue(promptContext.contains("htn_guide"))
    }

    @Test
    fun testCalculatorTool() = runTest {
        val calc = CalculatorTool()

        // Test 1: Complex operator precedence and brackets
        val args1 = "{\"expression\": \"12 * (4 + 6) / 2\"}"
        val res1 = calc.execute(args1)
        assertTrue(res1.isSuccess)
        assertEquals(60.0, res1.output.toDouble(), 0.001)

        // Test 2: Powers
        val args2 = "{\"expression\": \"2 ^ 8\"}"
        val res2 = calc.execute(args2)
        assertTrue(res2.isSuccess)
        assertEquals(256.0, res2.output.toDouble(), 0.001)

        // Test 3: Division by zero handled gracefully
        val args3 = "{\"expression\": \"100 / 0\"}"
        val res3 = calc.execute(args3)
        assertFalse(res3.isSuccess)
        assertTrue(res3.error?.contains("Division by zero") == true)
    }

    @Test
    fun testSystemTimeAndDeviceTelemetryTools() = runTest {
        val timeTool = SystemTimeTool()
        val timeRes = timeTool.execute("{}")
        assertTrue(timeRes.isSuccess)
        assertTrue(timeRes.output.contains("isoUtc"))
        assertTrue(timeRes.output.contains("timestampMs"))

        val teleTool = DeviceTelemetryTool(
            getBatteryPercent = { 92 },
            getIsCharging = { true },
            getAvailableRamMb = { 2400L },
            getThermalState = { "NORMAL" }
        )
        val teleRes = teleTool.execute("{}")
        assertTrue(teleRes.isSuccess)
        assertTrue(teleRes.output.contains("\"batteryPercent\":92"))
        assertTrue(teleRes.output.contains("\"availableRamMb\":2400"))
    }

    @Test
    fun testLocalMcpClient() = runTest {
        val calc = CalculatorTool()
        val mcpClient = LocalMcpClient("Test-MCP", listOf(calc))

        mcpClient.initialize()
        assertTrue(mcpClient.isConnected)

        val tools = mcpClient.listTools()
        assertEquals(1, tools.size)
        assertEquals("calculator", tools[0].name)

        val callArgs = buildJsonObject {
            put("expression", "25 * 4")
        }
        val callRes = mcpClient.callTool("calculator", callArgs)
        assertTrue(callRes.isSuccess)
        assertEquals("100.0", callRes.output)

        mcpClient.disconnect()
        assertFalse(mcpClient.isConnected)
    }
}
