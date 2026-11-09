package com.agentx.admin.server

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.os.Build
import com.agentx.core.protocol.p2p.P2PDataChannel
import com.agentx.core.protocol.p2p.StunClient
import com.agentx.core.protocol.pairing.PairingPayload
import com.agentx.core.protocol.pairing.QrCodeHelper
import com.agentx.core.protocol.pairing.RendezvousService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Manages Cloud Rendezvous 6-digit pairing, on-screen QR Code generation,
 * and P2P DataChannel listener on the AgentX Serve host device.
 */
class PairingManager(
    private val context: Context,
    private val server: AgentXAdminServer,
    private val localEngine: LocalModelServerEngine,
    private val tunnelManager: TunnelManager
) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("agentx_serve_pairing", Context.MODE_PRIVATE)
    private val scope = CoroutineScope(Dispatchers.IO + Job())
    private val rendezvousService = RendezvousService()

    val p2pChannel = P2PDataChannel(scope)

    private val _pairingCode = MutableStateFlow(loadOrGenerateCode())
    val pairingCode: StateFlow<String> = _pairingCode.asStateFlow()

    private val _qrBitmap = MutableStateFlow<Bitmap?>(null)
    val qrBitmap: StateFlow<Bitmap?> = _qrBitmap.asStateFlow()

    private val _isPublished = MutableStateFlow(false)
    val isPublished: StateFlow<Boolean> = _isPublished.asStateFlow()

    private val _p2pCandidate = MutableStateFlow<StunClient.StunCandidate?>(null)
    val p2pCandidate: StateFlow<StunClient.StunCandidate?> = _p2pCandidate.asStateFlow()

    init {
        setupP2pListener()
        // Generate initial QR code
        scope.launch {
            publishCoordinates()
        }
    }

    private fun loadOrGenerateCode(): String {
        val existing = prefs.getString("pairing_code", null)
        if (!existing.isNullOrBlank() && existing.length == 6) {
            return existing
        }
        val newCode = RendezvousService.generatePairingCode()
        prefs.edit().putString("pairing_code", newCode).apply()
        return newCode
    }

    fun refreshCode(): String {
        val newCode = RendezvousService.generatePairingCode()
        _pairingCode.value = newCode
        prefs.edit().putString("pairing_code", newCode).apply()
        scope.launch {
            publishCoordinates()
        }
        return newCode
    }

    /**
     * Publishes current server coordinates (LAN IP, WAN Tunnel URL, P2P Ticket, active model)
     * to the Cloud Rendezvous broker and updates the on-screen QR code bitmap.
     */
    suspend fun publishCoordinates(): Boolean = withContext(Dispatchers.IO) {
        val telem = server.telemetry.value
        val tunCfg = tunnelManager.config.value
        val code = _pairingCode.value

        val deviceName = "${Build.MANUFACTURER.replaceFirstChar { it.uppercase() }} ${Build.MODEL} - AgentX Serve"
        val localPort = telem.port
        val ip = telem.ipAddress
        val localEp = "http://$ip:$localPort/v1"
        val wanEp = if (tunCfg.isEnabled && !tunCfg.publicUrl.isNullOrBlank()) "${tunCfg.publicUrl}/v1" else null
        val activeModel = localEngine.activeModel.name.ifBlank { "medimind-v2-q5_k_m" }

        // Discover STUN reflexive candidate for P2P direct streaming
        if (_p2pCandidate.value == null) {
            val cand = p2pChannel.initialize(preferredPort = 49152)
            _p2pCandidate.value = cand
        }
        val cand = _p2pCandidate.value
        val p2pTicket = cand?.let { "udp:${it.publicIp}:${it.publicPort}" }

        val payload = PairingPayload(
            code = code,
            serverName = deviceName,
            localEndpoint = localEp,
            publicEndpoint = wanEp,
            apiKey = "ax-master-serve-key",
            activeModel = activeModel,
            p2pTicket = p2pTicket
        )

        // Generate QR code bitmap
        val qrString = payload.toQrString()
        val bmp = QrCodeHelper.generateQrBitmap(qrString, size = 512)
        _qrBitmap.value = bmp

        // Publish to Rendezvous broker
        val ok = rendezvousService.publish(payload)
        _isPublished.value = ok
        ok
    }

    private fun setupP2pListener() {
        p2pChannel.onRequestReceived = { req, onToken, onFinish ->
            val startTime = System.currentTimeMillis()
            var tokenCount = 0
            try {
                // Execute directly against the active local llama.cpp model
                localEngine.generateCompletionStream(req.prompt, req.systemPrompt, req.temperature).collect { token ->
                    tokenCount++
                    onToken(token)
                }
                val duration = System.currentTimeMillis() - startTime
                onFinish(tokenCount, duration)
            } catch (_: Exception) {
                onFinish(tokenCount, System.currentTimeMillis() - startTime)
            }
        }
    }
}
