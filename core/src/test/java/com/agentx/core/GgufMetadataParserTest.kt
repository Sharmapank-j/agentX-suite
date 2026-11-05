package com.agentx.core

import com.agentx.core.inference.gguf.GgufMetadataParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

class GgufMetadataParserTest {

    @Test
    fun testParseInvalidShortBuffer() {
        val emptyStream = ByteArrayInputStream(byteArrayOf(1, 2))
        val result = GgufMetadataParser.parse(emptyStream)
        assertFalse(result.isValid)
        assertTrue(result.errorMessage!!.contains("too short"))
    }

    @Test
    fun testParseInvalidMagic() {
        val badMagic = ByteArray(16) { 0 }
        val result = GgufMetadataParser.parse(ByteArrayInputStream(badMagic))
        assertFalse(result.isValid)
        assertTrue(result.errorMessage!!.contains("Invalid GGUF header magic"))
    }

    @Test
    fun testParseValidHeaderMock() {
        // Build mock binary GGUF header:
        // Magic (0x46554747), Version (3), TensorCount (100), KvCount (0)
        val buffer = ByteBuffer.allocate(32).order(ByteOrder.LITTLE_ENDIAN)
        buffer.putInt(0x46554747) // GGUF magic
        buffer.putInt(3)          // Version 3
        buffer.putLong(100L)      // 100 tensors
        buffer.putLong(0L)        // 0 KV pairs

        val stream = ByteArrayInputStream(buffer.array())
        val result = GgufMetadataParser.parse(stream)

        assertTrue(result.isValid)
        assertEquals(3, result.version)
        assertEquals(100L, result.tensorCount)
        assertEquals(0L, result.kvCount)
    }
}
