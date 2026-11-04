package com.agentx

import com.agentx.core.inference.HuggingFaceDownloader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HuggingFaceDownloaderTest {

    @Test
    fun testUrlSanitizationBlobToResolve() {
        val blobUrl = "https://huggingface.co/TheBloke/BioMistral-7B-GGUF/blob/main/biomistral-7b.Q4_K_M.gguf"
        val sanitized = HuggingFaceDownloader.sanitizeHuggingFaceUrl(blobUrl)

        assertEquals(
            "https://huggingface.co/TheBloke/BioMistral-7B-GGUF/resolve/main/biomistral-7b.Q4_K_M.gguf",
            sanitized
        )
    }

    @Test
    fun testUrlSanitizationDirectResolve() {
        val resolveUrl = "https://huggingface.co/TheBloke/meditron-7B-GGUF/resolve/main/meditron-7b.Q4_K_M.gguf"
        val sanitized = HuggingFaceDownloader.sanitizeHuggingFaceUrl(resolveUrl)

        assertEquals(resolveUrl, sanitized)
    }

    @Test
    fun testFilenameExtraction() {
        val url = "https://huggingface.co/Qwen/Qwen2.5-7B-Instruct-GGUF/resolve/main/qwen2.5-7b-instruct-q4_k_m.gguf"
        val filename = HuggingFaceDownloader.extractFilenameFromUrl(url)

        assertEquals("qwen2.5-7b-instruct-q4_k_m.gguf", filename)
    }

    @Test
    fun testCuratedModelsPresence() {
        val curated = HuggingFaceDownloader.CURATED_MODELS

        assertTrue(curated.isNotEmpty())
        val llama = curated.find { it.name.contains("Llama") }
        val qwen = curated.find { it.name.contains("Qwen") }

        assertTrue(llama != null)
        assertTrue(qwen != null)
        assertTrue(llama?.downloadUrl?.contains("resolve/main") == true)
        assertEquals("Q4_K_M", llama?.quantization)
    }
}
