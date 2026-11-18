package com.agentx.core.ota

import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest

/**
 * Validates cryptographic integrity and storage sandboxing for OTA component packages.
 */
object OtaVerifier {

    /**
     * Computes the SHA-256 checksum of [file] using streaming buffers.
     */
    fun computeSha256(file: File): String {
        require(file.exists() && file.isFile) { "File does not exist or is not a regular file: ${file.absolutePath}" }
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { input ->
            val buffer = ByteArray(8192)
            var bytesRead: Int
            while (input.read(buffer).also { bytesRead = it } != -1) {
                digest.update(buffer, 0, bytesRead)
            }
        }
        val hashBytes = digest.digest()
        val sb = StringBuilder(hashBytes.size * 2)
        for (b in hashBytes) {
            sb.append(String.format("%02x", b.toInt() and 0xFF))
        }
        return sb.toString()
    }

    /**
     * Checks if [file] matches [expectedSha256] case-insensitively.
     */
    fun verifySha256(file: File, expectedSha256: String): Boolean {
        if (!file.exists() || !file.isFile || expectedSha256.isBlank()) return false
        val actual = computeSha256(file)
        return actual.equals(expectedSha256.trim(), ignoreCase = true)
    }

    /**
     * Guarantees that [targetFile] is strictly contained inside [baseDir]
     * to eliminate path traversal attacks (e.g. "../../../").
     */
    fun isSafePath(baseDir: File, targetFile: File): Boolean {
        return try {
            val baseCanonical = baseDir.canonicalPath
            val targetCanonical = targetFile.canonicalPath
            targetCanonical.startsWith(baseCanonical)
        } catch (_: Exception) {
            false
        }
    }
}
