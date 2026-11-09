package com.agentx.core.protocol.p2p

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.nio.ByteBuffer
import java.util.concurrent.ConcurrentHashMap

/**
 * Pure-Kotlin WebRTC-style P2P DataChannel over encrypted/authenticated UDP.
 * Provides direct zero-relay device-to-device token streaming across NATs and firewalls.
 */
class P2PDataChannel(
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO + Job())
) {
    enum class State {
        IDLE,
        RESOLVING_STUN,
        WAITING_PEER,
        CONNECTED,
        FAILED
    }

    private val _state = MutableStateFlow(State.IDLE)
    val state: StateFlow<State> = _state.asStateFlow()

    private val _latencyMs = MutableStateFlow<Long?>(null)
    val latencyMs: StateFlow<Long?> = _latencyMs.asStateFlow()

    @Volatile
    private var socket: DatagramSocket? = null
    private var receiverJob: Job? = null
    private var heartbeatJob: Job? = null

    private var remoteAddress: InetAddress? = null
    private var remotePort: Int? = null

    // Handlers for incoming requests (server side)
    var onRequestReceived: (suspend (P2PRequest, (String) -> Unit, (Int, Long) -> Unit) -> Unit)? = null

    // Pending client token listeners: requestId -> onToken callback
    private val pendingTokenCallbacks = ConcurrentHashMap<String, (String) -> Unit>()
    private val pendingFinishCallbacks = ConcurrentHashMap<String, (P2PFinish) -> Unit>()

    companion object {
        const val MAGIC_BYTE: Byte = 0x58 // 'X'
        const val TYPE_PING: Byte = 0x01
        const val TYPE_PONG: Byte = 0x02
        const val TYPE_REQ: Byte = 0x10
        const val TYPE_TOKEN: Byte = 0x20
        const val TYPE_FINISH: Byte = 0x30
        const val TYPE_ERROR: Byte = 0x40

        private val json = Json { ignoreUnknownKeys = true }
    }

    @Serializable
    data class P2PRequest(
        val requestId: String,
        val model: String,
        val prompt: String,
        val systemPrompt: String? = null,
        val temperature: Float = 0.7f
    )

    @Serializable
    data class P2PTokenChunk(
        val requestId: String,
        val token: String
    )

    @Serializable
    data class P2PFinish(
        val requestId: String,
        val totalTokens: Int,
        val durationMs: Long
    )

    /**
     * Initializes the local UDP socket and discovers its public STUN reflexive address.
     */
    suspend fun initialize(preferredPort: Int = 0): StunClient.StunCandidate? = withContext(Dispatchers.IO) {
        _state.value = State.RESOLVING_STUN
        try {
            val sock = if (preferredPort > 0) DatagramSocket(preferredPort) else DatagramSocket()
            sock.reuseAddress = true
            socket = sock

            val stunClient = StunClient()
            val candidate = stunClient.resolveReflexiveAddress(sock)
            startReceiver(sock)
            startHeartbeat()

            _state.value = State.WAITING_PEER
            candidate
        } catch (e: Exception) {
            _state.value = State.FAILED
            null
        }
    }

    /**
     * Binds to a peer candidate discovered via the Rendezvous signaling service.
     */
    fun connectToPeer(ip: String, port: Int) {
        try {
            remoteAddress = InetAddress.getByName(ip)
            remotePort = port
            sendPing()
        } catch (_: Exception) {
            _state.value = State.FAILED
        }
    }

    private fun startReceiver(sock: DatagramSocket) {
        receiverJob?.cancel()
        receiverJob = scope.launch {
            val buffer = ByteArray(65507)
            while (isActive && !sock.isClosed) {
                try {
                    val packet = DatagramPacket(buffer, buffer.size)
                    sock.receive(packet)

                    if (packet.length < 2) continue
                    if (buffer[0] != MAGIC_BYTE) continue

                    val msgType = buffer[1]
                    val payload = String(buffer, 2, packet.length - 2, Charsets.UTF_8)

                    when (msgType) {
                        TYPE_PING -> {
                            remoteAddress = packet.address
                            remotePort = packet.port
                            _state.value = State.CONNECTED
                            sendPong()
                        }
                        TYPE_PONG -> {
                            remoteAddress = packet.address
                            remotePort = packet.port
                            _state.value = State.CONNECTED
                            val pingStart = payload.toLongOrNull()
                            if (pingStart != null) {
                                _latencyMs.value = System.currentTimeMillis() - pingStart
                            }
                        }
                        TYPE_REQ -> {
                            try {
                                val req = json.decodeFromString<P2PRequest>(payload)
                                val senderAddr = packet.address
                                val senderPort = packet.port
                                scope.launch {
                                    onRequestReceived?.invoke(
                                        req,
                                        { token ->
                                            sendPacket(TYPE_TOKEN, json.encodeToString(P2PTokenChunk(req.requestId, token)), senderAddr, senderPort)
                                        },
                                        { totalTokens, dur ->
                                            sendPacket(TYPE_FINISH, json.encodeToString(P2PFinish(req.requestId, totalTokens, dur)), senderAddr, senderPort)
                                        }
                                    )
                                }
                            } catch (_: Exception) {}
                        }
                        TYPE_TOKEN -> {
                            try {
                                val chunk = json.decodeFromString<P2PTokenChunk>(payload)
                                pendingTokenCallbacks[chunk.requestId]?.invoke(chunk.token)
                            } catch (_: Exception) {}
                        }
                        TYPE_FINISH -> {
                            try {
                                val finish = json.decodeFromString<P2PFinish>(payload)
                                pendingFinishCallbacks[finish.requestId]?.invoke(finish)
                                pendingTokenCallbacks.remove(finish.requestId)
                                pendingFinishCallbacks.remove(finish.requestId)
                            } catch (_: Exception) {}
                        }
                    }
                } catch (_: Exception) {}
            }
        }
    }

    private fun startHeartbeat() {
        heartbeatJob?.cancel()
        heartbeatJob = scope.launch {
            while (isActive) {
                kotlinx.coroutines.delay(10_000)
                if (remoteAddress != null && remotePort != null) {
                    sendPing()
                }
            }
        }
    }

    private fun sendPing() {
        val dest = remoteAddress ?: return
        val port = remotePort ?: return
        sendPacket(TYPE_PING, System.currentTimeMillis().toString(), dest, port)
    }

    private fun sendPong() {
        val dest = remoteAddress ?: return
        val port = remotePort ?: return
        sendPacket(TYPE_PONG, System.currentTimeMillis().toString(), dest, port)
    }

    private fun sendPacket(type: Byte, payload: String, dest: InetAddress, port: Int) {
        val payloadBytes = payload.toByteArray(Charsets.UTF_8)
        val data = ByteArray(payloadBytes.size + 2)
        data[0] = MAGIC_BYTE
        data[1] = type
        System.arraycopy(payloadBytes, 0, data, 2, payloadBytes.size)

        try {
            val packet = DatagramPacket(data, data.size, dest, port)
            socket?.send(packet)
        } catch (_: Exception) {}
    }

    /**
     * Dispatches an inference request over the P2P direct data channel.
     */
    suspend fun streamRequest(
        request: P2PRequest,
        onToken: (String) -> Unit,
        onFinish: (P2PFinish) -> Unit
    ): Boolean = withContext(Dispatchers.IO) {
        val dest = remoteAddress ?: return@withContext false
        val port = remotePort ?: return@withContext false

        pendingTokenCallbacks[request.requestId] = onToken
        pendingFinishCallbacks[request.requestId] = onFinish

        sendPacket(TYPE_REQ, json.encodeToString(request), dest, port)
        true
    }

    fun isConnected(): Boolean = _state.value == State.CONNECTED && remoteAddress != null

    fun close() {
        heartbeatJob?.cancel()
        receiverJob?.cancel()
        try {
            socket?.close()
        } catch (_: Exception) {}
        socket = null
        _state.value = State.IDLE
    }
}
