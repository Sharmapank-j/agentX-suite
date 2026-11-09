package com.agentx.core.protocol.p2p

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.security.SecureRandom

/**
 * RFC 5389 / RFC 3489 STUN Client for NAT discovery and hole punching.
 * Resolves the device's public reflexive IP and port through carrier-grade NATs (CGNAT).
 */
class StunClient(
    val stunHost: String = "stun.l.google.com",
    val stunPort: Int = 19302
) {
    data class StunCandidate(
        val publicIp: String,
        val publicPort: Int,
        val localPort: Int,
        val roundTripMs: Long
    )

    companion object {
        private const val MAGIC_COOKIE = 0x2112A442
        private val FALLBACK_SERVERS = listOf(
            "stun.l.google.com" to 19302,
            "stun1.l.google.com" to 19302,
            "stun.cloudflare.com" to 3478
        )
    }

    /**
     * Resolves the public reflexive address using the specified or fallback STUN servers.
     * @param localSocket Optional existing socket to preserve the local port mapping for hole punching.
     */
    suspend fun resolveReflexiveAddress(localSocket: DatagramSocket? = null): StunCandidate? = withContext(Dispatchers.IO) {
        val servers = listOf(stunHost to stunPort) + FALLBACK_SERVERS.filterNot { it.first == stunHost }
        for ((host, port) in servers) {
            val res = queryServer(host, port, localSocket)
            if (res != null) return@withContext res
        }
        null
    }

    private fun queryServer(host: String, port: Int, existingSocket: DatagramSocket?): StunCandidate? {
        val socket = existingSocket ?: DatagramSocket()
        val shouldClose = existingSocket == null
        return try {
            socket.soTimeout = 2500
            val targetAddress = InetAddress.getByName(host)

            // Construct 20-byte STUN Binding Request Header
            val transactionId = ByteArray(12)
            SecureRandom().nextBytes(transactionId)

            val req = ByteArray(20)
            req[0] = 0x00.toByte()
            req[1] = 0x01.toByte() // Binding Request (0x0001)
            req[2] = 0x00.toByte()
            req[3] = 0x00.toByte() // Length = 0

            // Magic Cookie: 0x2112A442
            req[4] = 0x21.toByte()
            req[5] = 0x12.toByte()
            req[6] = 0xA4.toByte()
            req[7] = 0x42.toByte()

            System.arraycopy(transactionId, 0, req, 8, 12)

            val sendPacket = DatagramPacket(req, req.size, targetAddress, port)
            val startTime = System.currentTimeMillis()
            socket.send(sendPacket)

            val respBuffer = ByteArray(512)
            val recvPacket = DatagramPacket(respBuffer, respBuffer.size)
            socket.receive(recvPacket)
            val latency = System.currentTimeMillis() - startTime

            // Validate response header
            if (recvPacket.length < 20) return null
            val msgType = ((respBuffer[0].toInt() and 0xFF) shl 8) or (respBuffer[1].toInt() and 0xFF)
            if (msgType != 0x0101) return null // Must be Binding Response (0x0101)

            // Parse Attributes
            var offset = 20
            val totalLen = Math.min(recvPacket.length, 20 + (((respBuffer[2].toInt() and 0xFF) shl 8) or (respBuffer[3].toInt() and 0xFF)))

            var mappedIp: String? = null
            var mappedPort: Int? = null

            while (offset + 4 <= totalLen) {
                val attrType = ((respBuffer[offset].toInt() and 0xFF) shl 8) or (respBuffer[offset + 1].toInt() and 0xFF)
                val attrLen = ((respBuffer[offset + 2].toInt() and 0xFF) shl 8) or (respBuffer[offset + 3].toInt() and 0xFF)
                val valueOffset = offset + 4

                if (valueOffset + attrLen > totalLen) break

                if (attrType == 0x0020 && attrLen >= 8) { // XOR-MAPPED-ADDRESS (RFC 5389)
                    val family = respBuffer[valueOffset + 1].toInt() and 0xFF
                    if (family == 0x01) { // IPv4
                        val xPort = ((respBuffer[valueOffset + 2].toInt() and 0xFF) shl 8) or (respBuffer[valueOffset + 3].toInt() and 0xFF)
                        val portNum = xPort xor (MAGIC_COOKIE ushr 16)

                        val ip0 = (respBuffer[valueOffset + 4].toInt() and 0xFF) xor (0x21)
                        val ip1 = (respBuffer[valueOffset + 5].toInt() and 0xFF) xor (0x12)
                        val ip2 = (respBuffer[valueOffset + 6].toInt() and 0xFF) xor (0xA4)
                        val ip3 = (respBuffer[valueOffset + 7].toInt() and 0xFF) xor (0x42)

                        mappedIp = "$ip0.$ip1.$ip2.$ip3"
                        mappedPort = portNum
                        break
                    }
                } else if (attrType == 0x0001 && attrLen >= 8) { // MAPPED-ADDRESS (RFC 3489)
                    val family = respBuffer[valueOffset + 1].toInt() and 0xFF
                    if (family == 0x01) {
                        val portNum = ((respBuffer[valueOffset + 2].toInt() and 0xFF) shl 8) or (respBuffer[valueOffset + 3].toInt() and 0xFF)
                        val ip0 = respBuffer[valueOffset + 4].toInt() and 0xFF
                        val ip1 = respBuffer[valueOffset + 5].toInt() and 0xFF
                        val ip2 = respBuffer[valueOffset + 6].toInt() and 0xFF
                        val ip3 = respBuffer[valueOffset + 7].toInt() and 0xFF
                        mappedIp = "$ip0.$ip1.$ip2.$ip3"
                        mappedPort = portNum
                    }
                }
                offset += 4 + ((attrLen + 3) and 3.inv()) // 4-byte boundary padding
            }

            if (mappedIp != null && mappedPort != null) {
                return StunCandidate(
                    publicIp = mappedIp,
                    publicPort = mappedPort,
                    localPort = socket.localPort,
                    roundTripMs = latency
                )
            }
            null
        } catch (_: Exception) {
            null
        } finally {
            if (shouldClose) {
                socket.close()
            }
        }
    }
}
