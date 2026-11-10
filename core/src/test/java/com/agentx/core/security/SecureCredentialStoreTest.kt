package com.agentx.core.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SecureCredentialStoreTest {

    @Test
    fun `test encrypt and decrypt roundtrip`() {
        val plainApiKey = "AIzaSyD-TestKey123456789_SafeSecureKey"
        val encrypted = SecureCredentialStore.encrypt(plainApiKey)

        assertTrue("Encrypted text must have enc prefix", encrypted.startsWith("enc:v1:"))
        assertNotEquals("Encrypted text must not equal plaintext", plainApiKey, encrypted)

        val decrypted = SecureCredentialStore.decrypt(encrypted)
        assertEquals("Decrypted key must match original plaintext exactly", plainApiKey, decrypted)
    }

    @Test
    fun `test legacy plaintext backwards compatibility`() {
        val legacyKey = "AIzaSyLegacyKeyWithoutEncryption"
        val decrypted = SecureCredentialStore.decrypt(legacyKey)
        assertEquals("Legacy plaintext keys without enc prefix must be preserved as-is", legacyKey, decrypted)
    }

    @Test
    fun `test empty and blank inputs`() {
        assertEquals("", SecureCredentialStore.encrypt(""))
        assertEquals("", SecureCredentialStore.encrypt("   "))
        assertEquals("", SecureCredentialStore.decrypt(""))
    }
}
