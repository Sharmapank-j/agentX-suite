package com.agentx.core.protocol.pairing

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.security.SecureRandom

/**
 * Cloud Rendezvous & Signaling Broker Client.
 *
 * Facilitates automatic discovery and pairing between AgentX Serve (server phone)
 * and AgentX Client (client phone) across heterogeneous networks, NATs, and firewalls.
 *
 * Uses a lightweight, zero-configuration key-value rendezvous broker (such as KVdb.io,
 * Cloudflare Worker KV, or a custom Supabase/Firebase function).
 */
class RendezvousService(
    private val customBrokerBaseUrl: String? = null
) {
    companion object {
        // Default public zero-signup KV broker bucket for ephemeral pairing keys
        private const val DEFAULT_BROKER_URL = "https://kvdb.io/8rP7Z6s6nL4mK3q2"
        private val random = SecureRandom()

        /**
         * Generates a human-friendly 6-digit pairing code (e.g., "749216").
         */
        fun generatePairingCode(): String {
            val num = 100_000 + random.nextInt(900_000)
            return num.toString()
        }
    }

    private val brokerUrl: String
        get() = customBrokerBaseUrl?.trimEnd('/') ?: DEFAULT_BROKER_URL

    /**
     * Publishes the server's pairing coordinates to the cloud rendezvous broker under the 6-digit code.
     * @return true if successfully published, false otherwise.
     */
    suspend fun publish(payload: PairingPayload): Boolean = withContext(Dispatchers.IO) {
        val sanitizedCode = payload.code.trim().replace("-", "")
        if (sanitizedCode.length != 6) return@withContext false

        val endpointUrl = "$brokerUrl/pair_$sanitizedCode?ttl=${payload.ttlSeconds}"
        var conn: HttpURLConnection? = null
        try {
            val url = URL(endpointUrl)
            conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                connectTimeout = 6000
                readTimeout = 6000
                doOutput = true
            }

            val jsonBody = PairingPayload.toJson(payload)
            OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { writer ->
                writer.write(jsonBody)
                writer.flush()
            }

            val code = conn.responseCode
            code in 200..299
        } catch (_: Exception) {
            // Fallback: try PUT method if POST was rejected
            try {
                val putConn = (URL(endpointUrl).openConnection() as HttpURLConnection).apply {
                    requestMethod = "PUT"
                    setRequestProperty("Content-Type", "application/json; charset=utf-8")
                    connectTimeout = 6000
                    readTimeout = 6000
                    doOutput = true
                }
                OutputStreamWriter(putConn.outputStream, Charsets.UTF_8).use {
                    it.write(PairingPayload.toJson(payload))
                    it.flush()
                }
                val code = putConn.responseCode
                putConn.disconnect()
                code in 200..299
            } catch (_: Exception) {
                false
            }
        } finally {
            conn?.disconnect()
        }
    }

    /**
     * Resolves a 6-digit pairing code from the rendezvous broker to discover the server's endpoint.
     * @param code The 6-digit code entered by the user (supports "123456" or "123-456").
     * @return The resolved PairingPayload, or null if expired/not found.
     */
    suspend fun resolve(code: String): PairingPayload? = withContext(Dispatchers.IO) {
        val sanitized = code.trim().replace("-", "").replace(" ", "")
        if (sanitized.length != 6) return@withContext null

        val endpointUrl = "$brokerUrl/pair_$sanitized"
        var conn: HttpURLConnection? = null
        try {
            val url = URL(endpointUrl)
            conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                setRequestProperty("Accept", "application/json")
                connectTimeout = 6000
                readTimeout = 6000
            }

            val respCode = conn.responseCode
            if (respCode !in 200..299) return@withContext null

            val body = conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            if (body.isBlank()) return@withContext null

            PairingPayload.fromJson(body)
        } catch (_: Exception) {
            null
        } finally {
            conn?.disconnect()
        }
    }
}
