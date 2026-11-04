package com.agentx

import com.agentx.core.inference.GgufMetadataParser
import com.agentx.domain.models.GGUFModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

class GgufMetadataParserTest {

    @Test
    fun testValidGgufV3HeaderParsing() {
        val buffer = ByteBuffer.allocate(1024).order(ByteOrder.LITTLE_ENDIAN)

        // 1. Magic "GGUF"
        buffer.put('G'.code.toByte())
        buffer.put('G'.code.toByte())
        buffer.put('U'.code.toByte())
        buffer.put('F'.code.toByte())

        // 2. Version 3
        buffer.putInt(3)

        // 3. Tensor count (128)
        buffer.putLong(128L)

        // 4. KV count (3 entries)
        buffer.putLong(3L)

        // Entry 1: "general.architecture" -> "llama"
        writeGgufString(buffer, "general.architecture")
        buffer.putInt(8) // TYPE_STRING
        writeGgufString(buffer, "llama")

        // Entry 2: "llama.context_length" -> 4096 (int32)
        writeGgufString(buffer, "llama.context_length")
        buffer.putInt(4) // TYPE_UINT32
        buffer.putInt(4096)

        // Entry 3: "general.file_type" -> 15 (Q4_K_M)
        writeGgufString(buffer, "general.file_type")
        buffer.putInt(4) // TYPE_UINT32
        buffer.putInt(15)

        val stream = ByteArrayInputStream(buffer.array(), 0, buffer.position())
        val metadata = GgufMetadataParser.parse(stream)

        assertTrue(metadata.isValid)
        assertEquals(3, metadata.version)
        assertEquals("llama", metadata.architecture)
        assertEquals(4096, metadata.contextLength)
        assertEquals(128L, metadata.tensorCount)
        assertEquals("Q4_K_M", metadata.quantization)
    }

    @Test
    fun testInvalidMagicBytesRejected() {
        val buffer = ByteBuffer.allocate(32).order(ByteOrder.LITTLE_ENDIAN)
        buffer.putInt(0x12345678) // Not GGUF
        buffer.putInt(3)

        val stream = ByteArrayInputStream(buffer.array(), 0, buffer.position())
        val metadata = GgufMetadataParser.parse(stream)

        assertFalse(metadata.isValid)
        assertTrue(metadata.errorMessage?.contains("Invalid GGUF header magic") == true)
    }

    @Test
    fun testTruncatedBufferGracefullyHandled() {
        val stream = ByteArrayInputStream(byteArrayOf(0x47, 0x47)) // Only 2 bytes
        val metadata = GgufMetadataParser.parse(stream)

        assertFalse(metadata.isValid)
        assertTrue(metadata.errorMessage?.contains("too short") == true)
    }

    @Test
    fun testUnsupportedVersionRejected() {
        val buffer = ByteBuffer.allocate(32).order(ByteOrder.LITTLE_ENDIAN)
        buffer.put('G'.code.toByte())
        buffer.put('G'.code.toByte())
        buffer.put('U'.code.toByte())
        buffer.put('F'.code.toByte())
        buffer.putInt(99) // Unsupported version 99
        buffer.putLong(10L)
        buffer.putLong(0L)

        val stream = ByteArrayInputStream(buffer.array(), 0, buffer.position())
        val metadata = GgufMetadataParser.parse(stream)

        assertFalse(metadata.isValid)
        assertTrue(metadata.errorMessage?.contains("Unsupported GGUF version") == true)
    }

    @Test
    fun testModelStagingAndProfileAttributes() {
        val model = GGUFModel(
            id = "med_model_1",
            name = "BioMistral-7B",
            filePath = "/data/user/0/com.agentx/files/models/biomistral_q4km.gguf",
            uriString = "content://saf/biomistral.gguf",
            sizeBytes = 4_300_000_000L,
            architecture = "mistral",
            contextLength = 4096,
            tensorCount = 291L,
            quantization = "Q4_K_M",
            capabilityProfile = "medical-clinical",
            isStaged = true,
            isActive = true,
            threads = 6,
            temperature = 0.3f,
            topP = 0.95f
        )

        assertTrue(model.isStaged)
        assertTrue(model.isActive)
        assertEquals("mistral", model.architecture)
        assertEquals("medical-clinical", model.capabilityProfile)
        assertEquals(6, model.threads)
        assertEquals(0.3f, model.temperature, 0.001f)
    }

    private fun writeGgufString(buffer: ByteBuffer, value: String) {
        val bytes = value.toByteArray(Charsets.UTF_8)
        buffer.putLong(bytes.size.toLong())
        buffer.put(bytes)
    }
}
