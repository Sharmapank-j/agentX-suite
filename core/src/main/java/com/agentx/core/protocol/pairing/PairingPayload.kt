package com.agentx.core.protocol.pairing

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.net.URLDecoder
import java.net.URLEncoder

/**
 * Ecosystem pairing payload shared between AgentX Serve (admin) and AgentX Client (app).
 * Encapsulates all connection coordinates, active model metadata, and optional P2P session ticket.
 */
@Serializable
data class PairingPayload(
    val code: String,                     // 6-digit pairing code (e.g., "592814")
    val serverName: String,               // Friendly device identifier (e.g., "Poco X4 Pro 5G")
    val localEndpoint: String,            // LAN Base URL (e.g., "http://10.212.66.224:8080/v1")
    val publicEndpoint: String? = null,   // WAN Reverse Tunnel URL (e.g., "https://xxxx.a.pinggy.link/v1")
    val apiKey: String = "ax-master-serve-key",
    val activeModel: String = "",
    val p2pTicket: String? = null,        // Optional STUN candidate / P2P ticket (e.g. "udp:192.168.1.5:49152")
    val publishedAtMs: Long = System.currentTimeMillis(),
    val ttlSeconds: Int = 1800            // 30 minute time-to-live
) {
    /**
     * Determines the optimal endpoint for a client connecting from WAN or LAN.
     */
    fun preferredEndpoint(): String {
        return publicEndpoint?.takeIf { it.isNotBlank() } ?: localEndpoint
    }

    /**
     * Serializes this payload into an ecosystem pairing URI suitable for QR codes and deep links.
     */
    fun toQrString(): String {
        val ep = preferredEndpoint()
        val enc = { s: String -> URLEncoder.encode(s, "UTF-8") }
        return "agentx://pair?code=${enc(code)}&endpoint=${enc(ep)}&key=${enc(apiKey)}&model=${enc(activeModel)}&name=${enc(serverName)}&local=${enc(localEndpoint)}" +
                (publicEndpoint?.let { "&wan=${enc(it)}" } ?: "") +
                (p2pTicket?.let { "&p2p=${enc(it)}" } ?: "")
    }

    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        fun fromJson(jsonStr: String): PairingPayload? {
            return try {
                json.decodeFromString<PairingPayload>(jsonStr)
            } catch (_: Exception) {
                null
            }
        }

        fun toJson(payload: PairingPayload): String {
            return json.encodeToString(payload)
        }

        fun fromQrString(qr: String): PairingPayload? {
            return try {
                if (!qr.startsWith("agentx://pair?")) {
                    // Try parsing as raw JSON
                    return fromJson(qr)
                }
                val queryString = qr.substringAfter("agentx://pair?")
                val params = queryString.split("&").associate { param ->
                    val parts = param.split("=", limit = 2)
                    val k = parts[0]
                    val v = if (parts.size > 1) URLDecoder.decode(parts[1], "UTF-8") else ""
                    k to v
                }

                val code = params["code"] ?: return null
                val endpoint = params["endpoint"] ?: params["local"] ?: return null
                val apiKey = params["key"] ?: "ax-master-serve-key"
                val model = params["model"] ?: ""
                val name = params["name"] ?: "AgentX Serve"
                val wan = params["wan"]
                val local = params["local"] ?: endpoint
                val p2p = params["p2p"]

                PairingPayload(
                    code = code,
                    serverName = name,
                    localEndpoint = local,
                    publicEndpoint = wan,
                    apiKey = apiKey,
                    activeModel = model,
                    p2pTicket = p2p
                )
            } catch (_: Exception) {
                null
            }
        }
    }
}
