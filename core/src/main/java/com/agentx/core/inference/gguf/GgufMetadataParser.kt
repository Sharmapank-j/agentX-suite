package com.agentx.core.inference.gguf

import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

data class GgufMetadata(
    val isValid: Boolean,
    val version: Int = 0,
    val architecture: String = "unknown",
    val contextLength: Int = 2048,
    val tensorCount: Long = 0L,
    val kvCount: Long = 0L,
    val quantization: String = "Q4_K_M",
    val name: String = "",
    val chatTemplate: String? = null,
    val errorMessage: String? = null
)

object GgufMetadataParser {

    private const val GGUF_MAGIC = 0x46554747 // "GGUF" in Little Endian (0x47, 0x47, 0x55, 0x46)

    // GGUF Metadata Value Types
    private const val TYPE_UINT8 = 0
    private const val TYPE_INT8 = 1
    private const val TYPE_UINT16 = 2
    private const val TYPE_INT16 = 3
    private const val TYPE_UINT32 = 4
    private const val TYPE_INT32 = 5
    private const val TYPE_FLOAT32 = 6
    private const val TYPE_BOOL = 7
    private const val TYPE_STRING = 8
    private const val TYPE_ARRAY = 9
    private const val TYPE_UINT64 = 10
    private const val TYPE_INT64 = 11
    private const val TYPE_FLOAT64 = 12

    fun parse(inputStream: InputStream, maxKvToRead: Int = 256): GgufMetadata {
        return try {
            val headerBuffer = ByteArray(4)
            if (readExact(inputStream, headerBuffer) < 4) {
                return GgufMetadata(isValid = false, errorMessage = "File is too short to be a GGUF file")
            }

            val magic = ByteBuffer.wrap(headerBuffer).order(ByteOrder.LITTLE_ENDIAN).int
            if (magic != GGUF_MAGIC) {
                return GgufMetadata(
                    isValid = false,
                    errorMessage = "Invalid GGUF header magic. Expected 'GGUF', found: 0x${Integer.toHexString(magic)}"
                )
            }

            // Version (uint32)
            val version = readInt32(inputStream)
            if (version < 1 || version > 4) {
                return GgufMetadata(isValid = false, errorMessage = "Unsupported GGUF version: $version")
            }

            // Tensor count (uint64)
            val tensorCount = readInt64(inputStream)

            // KV count (uint64)
            val kvCount = readInt64(inputStream)

            var architecture = "unknown"
            var name = ""
            var contextLength = 2048
            var quantization = "Q4_K_M"
            var chatTemplate: String? = null

            val entriesToRead = minOf(kvCount, maxKvToRead.toLong()).toInt()

            for (i in 0 until entriesToRead) {
                val key = readString(inputStream) ?: break
                val valType = readInt32(inputStream)

                when (valType) {
                    TYPE_UINT8, TYPE_INT8, TYPE_BOOL -> {
                        inputStream.skip(1)
                    }
                    TYPE_UINT16, TYPE_INT16 -> {
                        inputStream.skip(2)
                    }
                    TYPE_UINT32, TYPE_INT32 -> {
                        val num = readInt32(inputStream)
                        if (key.endsWith(".context_length")) {
                            contextLength = minOf(num, 3840).coerceAtLeast(512)
                        } else if (key == "general.file_type") {
                            quantization = mapFileTypeToQuant(num)
                        }
                    }
                    TYPE_FLOAT32 -> {
                        inputStream.skip(4)
                    }
                    TYPE_UINT64, TYPE_INT64 -> {
                        val num = readInt64(inputStream)
                        if (key.endsWith(".context_length")) {
                            contextLength = minOf(num.toInt(), 3840).coerceAtLeast(512)
                        }
                    }
                    TYPE_FLOAT64 -> {
                        inputStream.skip(8)
                    }
                    TYPE_STRING -> {
                        val strVal = readString(inputStream) ?: ""
                        when (key) {
                            "general.architecture" -> architecture = strVal
                            "general.name" -> name = strVal
                            "tokenizer.chat_template" -> chatTemplate = strVal
                        }
                    }
                    TYPE_ARRAY -> {
                        skipArray(inputStream)
                    }
                    else -> {
                        break
                    }
                }
            }

            GgufMetadata(
                isValid = true,
                version = version,
                architecture = architecture,
                contextLength = contextLength,
                tensorCount = tensorCount,
                kvCount = kvCount,
                quantization = quantization,
                name = name.ifEmpty { "GGUF Model" },
                chatTemplate = chatTemplate
            )
        } catch (e: Exception) {
            GgufMetadata(isValid = false, errorMessage = "Parsing error: ${e.message}")
        }
    }

    private fun readExact(inputStream: InputStream, buffer: ByteArray): Int {
        var total = 0
        while (total < buffer.size) {
            val count = inputStream.read(buffer, total, buffer.size - total)
            if (count == -1) break
            total += count
        }
        return total
    }

    private fun readInt32(inputStream: InputStream): Int {
        val buf = ByteArray(4)
        readExact(inputStream, buf)
        return ByteBuffer.wrap(buf).order(ByteOrder.LITTLE_ENDIAN).int
    }

    private fun readInt64(inputStream: InputStream): Long {
        val buf = ByteArray(8)
        readExact(inputStream, buf)
        return ByteBuffer.wrap(buf).order(ByteOrder.LITTLE_ENDIAN).long
    }

    private fun readString(inputStream: InputStream): String? {
        val len = readInt64(inputStream)
        if (len < 0 || len > 1024 * 64) return null
        val strBytes = ByteArray(len.toInt())
        val read = readExact(inputStream, strBytes)
        if (read < len.toInt()) return null
        return String(strBytes, Charsets.UTF_8)
    }

    private fun skipFully(inputStream: InputStream, toSkip: Long) {
        var remaining = toSkip
        val buf = ByteArray(minOf(remaining.coerceAtLeast(1), 8192L).toInt())
        while (remaining > 0) {
            val skipped = inputStream.skip(remaining)
            if (skipped > 0) {
                remaining -= skipped
            } else {
                val read = inputStream.read(buf, 0, minOf(remaining, buf.size.toLong()).toInt())
                if (read == -1) break
                remaining -= read
            }
        }
    }

    private fun skipArray(inputStream: InputStream) {
        val elemType = readInt32(inputStream)
        val arrayLen = readInt64(inputStream)
        if (arrayLen < 0 || arrayLen > 500_000) return

        when (elemType) {
            TYPE_UINT8, TYPE_INT8, TYPE_BOOL -> skipFully(inputStream, arrayLen)
            TYPE_UINT16, TYPE_INT16 -> skipFully(inputStream, arrayLen * 2)
            TYPE_UINT32, TYPE_INT32, TYPE_FLOAT32 -> skipFully(inputStream, arrayLen * 4)
            TYPE_UINT64, TYPE_INT64, TYPE_FLOAT64 -> skipFully(inputStream, arrayLen * 8)
            TYPE_STRING -> {
                for (j in 0 until arrayLen) {
                    val sLen = readInt64(inputStream)
                    if (sLen in 0..65536) {
                        skipFully(inputStream, sLen)
                    } else {
                        break
                    }
                }
            }
        }
    }

    private fun mapFileTypeToQuant(fileType: Int): String {
        return when (fileType) {
            0 -> "F32"
            1 -> "F16"
            2 -> "Q4_0"
            3 -> "Q4_1"
            7 -> "Q8_0"
            8 -> "Q5_0"
            9 -> "Q5_1"
            10 -> "Q2_K"
            11 -> "Q3_K_S"
            12 -> "Q3_K_M"
            13 -> "Q3_K_L"
            14 -> "Q4_K_S"
            15 -> "Q4_K_M"
            16 -> "Q5_K_S"
            17 -> "Q5_K_M"
            18 -> "Q6_K"
            19 -> "IQ2_XXS"
            20 -> "IQ2_XS"
            21 -> "Q2_K_S"
            22 -> "IQ3_XS"
            23 -> "IQ3_XXS"
            24 -> "IQ1_S"
            25 -> "IQ4_NL"
            26 -> "IQ3_S"
            27 -> "IQ3_M"
            28 -> "IQ2_S"
            29 -> "IQ2_M"
            30 -> "IQ4_XS"
            else -> "Q4_K_M"
        }
    }
}
