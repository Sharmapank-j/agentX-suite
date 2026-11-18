package com.agentx.admin.server

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.agentx.admin.models.TunnelConfig
import com.agentx.admin.models.TunnelProvider
import com.agentx.admin.models.TunnelStatus
import com.jcraft.jsch.Channel
import com.jcraft.jsch.ChannelShell
import com.jcraft.jsch.JSch
import com.jcraft.jsch.KeyPair
import com.jcraft.jsch.Session
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.Properties
import java.util.UUID
import java.util.regex.Pattern

/**
 * Manages zero-configuration remote tunneling for AgentX Serve.
 *
 * Implements an active in-app reverse tunnel engine using pure-Java/Kotlin SSH reverse
 * port-forwarding (via JSch to Pinggy.io / Cloudflare / Custom Relays). Enables old phones
 * serving local LLMs on a home Wi-Fi subnet or cellular data to expose secure public HTTPS endpoints
 * across carrier-grade NATs (CGNAT) without router port-forwarding or network restriction.
 */
class TunnelManager(
    private val context: Context? = null,
    private val customPrefs: SharedPreferences? = null
) {
    private val prefs: SharedPreferences? = customPrefs ?: context?.getSharedPreferences("agentx_serve_tunnel", Context.MODE_PRIVATE)
    private val tunnelScope = CoroutineScope(Dispatchers.IO + Job())

    private val _config = MutableStateFlow(loadConfig())
    val config: StateFlow<TunnelConfig> = _config.asStateFlow()

    @Volatile
    private var activeSession: Session? = null
    @Volatile
    private var activeChannel: Channel? = null
    private var tunnelWorkerJob: Job? = null
    private var cachedIdentity: Pair<ByteArray, ByteArray>? = null

    companion object {
        private val PINGGY_HOSTS = listOf("free.pinggy.io", "a.pinggy.io")
        private const val PINGGY_PORT_SSL = 443
        private const val PINGGY_PORT_SSH = 22
        private val URL_REGEX = Pattern.compile("https://[a-zA-Z0-9.-]+\\.(?:free\\.pinggy\\.net|run\\.pinggy-free\\.link|free\\.pinggy\\.link|pinggy\\.link|a\\.pinggy\\.link|pinggy\\.io|trycloudflare\\.com|ngrok-free\\.app|lhr\\.life)")
        private val ANSI_REGEX = "\u001B\\[[;\\d]*[ -/]*[@-~]".toRegex()
    }

    private fun loadConfig(): TunnelConfig {
        if (prefs == null) {
            return TunnelConfig()
        }
        val enabled = prefs.getBoolean("tunnel_enabled", true)
        val providerStr = prefs.getString("tunnel_provider", TunnelProvider.PINGGY.name) ?: TunnelProvider.PINGGY.name
        val provider = try { TunnelProvider.valueOf(providerStr) } catch (_: Exception) { TunnelProvider.PINGGY }
        val authToken = prefs.getString("tunnel_auth_token", "") ?: ""
        val customDomain = prefs.getString("tunnel_custom_domain", "") ?: ""

        // Ephemeral reverse SSH tunnels must never assume a past session is active on startup
        return TunnelConfig(
            isEnabled = enabled,
            provider = provider,
            publicUrl = null,
            authToken = authToken,
            customDomain = customDomain,
            status = TunnelStatus.STOPPED
        )
    }

    private fun saveConfig(newConfig: TunnelConfig) {
        prefs?.edit()
            ?.putBoolean("tunnel_enabled", newConfig.isEnabled)
            ?.putString("tunnel_provider", newConfig.provider.name)
            ?.putString("tunnel_public_url", newConfig.publicUrl)
            ?.putString("tunnel_auth_token", newConfig.authToken)
            ?.putString("tunnel_custom_domain", newConfig.customDomain)
            ?.apply()
    }

    fun startTunnel(
        provider: TunnelProvider = TunnelProvider.PINGGY,
        token: String = "",
        customDomain: String = "",
        localPort: Int = 8080
    ): TunnelConfig {
        Log.i("TunnelManager", "startTunnel called: provider=$provider, localPort=$localPort, token=${token.take(4)}***")
        stopTunnelInternal()

        val initialConfig = TunnelConfig(
            isEnabled = true,
            provider = provider,
            publicUrl = null,
            authToken = token,
            customDomain = customDomain,
            status = TunnelStatus.STARTING,
            lastError = null
        )
        _config.value = initialConfig
        saveConfig(initialConfig)

        tunnelWorkerJob = tunnelScope.launch {
            while (isActive && _config.value.isEnabled) {
                try {
                    Log.i("TunnelManager", "tunnelWorkerJob loop active for $provider on port $localPort")
                    when (provider) {
                        TunnelProvider.PINGGY -> {
                            establishPinggySshTunnel(token, customDomain, localPort)
                        }
                        TunnelProvider.CUSTOM_RELAY -> {
                            if (customDomain.isNotBlank() && (customDomain.startsWith("http://") || customDomain.startsWith("https://"))) {
                                val activeUrl = customDomain.trimEnd('/')
                                Log.i("TunnelManager", "CUSTOM_RELAY configured: $activeUrl")
                                _config.value = _config.value.copy(
                                    publicUrl = activeUrl,
                                    status = TunnelStatus.CONNECTED,
                                    lastError = null
                                )
                                saveConfig(_config.value)
                                while (isActive && _config.value.isEnabled) {
                                    delay(5000)
                                }
                            } else {
                                establishPinggySshTunnel(token, customDomain, localPort)
                            }
                        }
                        TunnelProvider.CLOUDFLARE, TunnelProvider.NGROK -> {
                            establishPinggySshTunnel(token, customDomain, localPort)
                        }
                    }
                } catch (e: Throwable) {
                    if (e is kotlinx.coroutines.CancellationException) throw e
                    Log.e("TunnelManager", "Error in tunnelWorkerJob", e)
                    _config.value = _config.value.copy(
                        status = TunnelStatus.ERROR,
                        lastError = "Tunnel initialization failed: ${e.message}"
                    )
                }

                if (isActive && _config.value.isEnabled) {
                    Log.i("TunnelManager", "Tunnel session ended. Auto-reconnecting in 3 seconds...")
                    _config.value = _config.value.copy(
                        status = TunnelStatus.STARTING,
                        lastError = "Reconnecting to tunnel broker..."
                    )
                    delay(3000)
                }
            }
        }

        return _config.value
    }

    private suspend fun establishPinggySshTunnel(
        token: String,
        customDomain: String,
        localPort: Int
    ): Unit = withContext(Dispatchers.IO) {
        Log.i("TunnelManager", "establishPinggySshTunnel executing...")
        val jsch = JSch()

        // Generate and cache in-memory ephemeral RSA 2048 keypair so handshake succeeds without interactive password prompt
        try {
            val ident = cachedIdentity ?: run {
                val kp = KeyPair.genKeyPair(jsch, KeyPair.RSA, 2048)
                val prv = ByteArrayOutputStream()
                val pub = ByteArrayOutputStream()
                kp.writePrivateKey(prv)
                kp.writePublicKey(pub, "agentx-serve")
                val p = Pair(prv.toByteArray(), pub.toByteArray())
                cachedIdentity = p
                p
            }
            jsch.addIdentity("agentx-ephemeral", ident.first, ident.second, null)
        } catch (e: Exception) {
            Log.w("TunnelManager", "Identity generation warning: ${e.message}")
        }

        val username = if (token.isNotBlank()) "a+$token" else "a"

        var session: Session? = null
        var lastErr: Exception? = null

        // Try free.pinggy.io:443, a.pinggy.io:443, then port 22
        for (host in PINGGY_HOSTS) {
            for (port in listOf(PINGGY_PORT_SSL, PINGGY_PORT_SSH)) {
                try {
                    session = jsch.getSession(username, host, port)
                    val config = Properties().apply {
                        put("StrictHostKeyChecking", "no")
                    }
                    session.setConfig(config)
                    session.serverAliveInterval = 15_000
                    session.serverAliveCountMax = 4
                    session.timeout = 10_000
                    session.connect(10_000)
                    if (session.isConnected) {
                        Log.i("TunnelManager", "Session successfully connected to $host:$port")
                        break
                    }
                } catch (e: Exception) {
                    lastErr = e
                    Log.w("TunnelManager", "Failed to connect to $host:$port: ${e.message}")
                    try { session?.disconnect() } catch (_: Exception) {}
                    session = null
                }
            }
            if (session?.isConnected == true) break
        }

        if (session == null || !session.isConnected) {
            val errMsg = "Failed to connect to tunnel broker: ${lastErr?.message ?: "Timeout"}"
            Log.e("TunnelManager", errMsg)
            _config.value = _config.value.copy(
                status = TunnelStatus.ERROR,
                lastError = errMsg
            )
            return@withContext
        }

        activeSession = session

        try {
            // Reverse port forwarding: 0 = dynamic remote port allocation on broker
            session.setPortForwardingR(0, "127.0.0.1", localPort)
            Log.i("TunnelManager", "setPortForwardingR(0, 127.0.0.1, $localPort) registered")

            // Open shell channel with PTY for terminal session negotiation
            val channel = session.openChannel("shell") as ChannelShell
            channel.setPty(true)
            channel.setPtyType("vt100")
            activeChannel = channel
            val inputStream: InputStream = channel.inputStream
            channel.connect(5000)
            Log.i("TunnelManager", "Shell channel connected: ${channel.isConnected}")

            val buf = ByteArray(1024)
            val fullOutput = StringBuilder()
            var detectedUrl: String? = null
            val startTime = System.currentTimeMillis()

            // Read welcome lines to detect allocated public HTTPS URL
            while (isActive && session.isConnected && (System.currentTimeMillis() - startTime < 15_000)) {
                val available = inputStream.available()
                if (available > 0) {
                    val len = inputStream.read(buf, 0, minOf(available, buf.size))
                    if (len > 0) {
                        val cleanChunk = String(buf, 0, len, Charsets.UTF_8).replace(ANSI_REGEX, "")
                        fullOutput.append(cleanChunk)
                        Log.d("TunnelManager", "Banner chunk: $cleanChunk")
                        val matcher = URL_REGEX.matcher(fullOutput.toString())
                        while (matcher.find()) {
                            val candidate = matcher.group(0)
                            if (candidate != null && !candidate.contains("dashboard.pinggy.io")) {
                                detectedUrl = candidate
                                Log.i("TunnelManager", "Found valid allocated public URL: $detectedUrl")
                                break
                            }
                        }
                        if (detectedUrl != null) break
                    }
                } else {
                    delay(100)
                }
            }

            if (detectedUrl != null) {
                Log.i("TunnelManager", "Tunnel established with Public URL: $detectedUrl")
                _config.value = _config.value.copy(
                    publicUrl = detectedUrl,
                    status = TunnelStatus.CONNECTED,
                    lastError = null
                )
                saveConfig(_config.value)
            } else {
                Log.w("TunnelManager", "No URL detected in 15s. Full output was:\n$fullOutput")
                _config.value = _config.value.copy(
                    publicUrl = null,
                    status = TunnelStatus.ERROR,
                    lastError = "Tunnel broker did not assign a public URL in 15s"
                )
                return@withContext
            }

            // Continuously drain shell output in background IO so SSH channel window never fills or stalls
            val drainJob = launch(Dispatchers.IO) {
                val drainBuf = ByteArray(8192)
                try {
                    while (isActive && channel.isConnected) {
                        val n = inputStream.read(drainBuf)
                        if (n < 0) break
                    }
                } catch (_: Exception) {}
            }

            // Monitor session health and send periodic SSH keepalives every 5 seconds
            while (isActive && session.isConnected && channel.isConnected) {
                delay(5000)
                try {
                    session.sendKeepAliveMsg()
                } catch (e: Exception) {
                    Log.w("TunnelManager", "Session keepalive failed: ${e.message}")
                    break
                }
            }

            drainJob.cancel()
            Log.i("TunnelManager", "Pinggy SSH session connection closed.")
        } catch (e: Exception) {
            if (_config.value.isEnabled) {
                Log.w("TunnelManager", "Pinggy SSH tunnel exception: ${e.message}")
                _config.value = _config.value.copy(
                    status = TunnelStatus.ERROR,
                    lastError = e.message ?: "Tunnel communication error"
                )
            }
        } finally {
            try { activeChannel?.disconnect() } catch (_: Exception) {}
            activeChannel = null
            try { activeSession?.disconnect() } catch (_: Exception) {}
            activeSession = null
        }
    }

    private fun stopTunnelInternal() {
        tunnelWorkerJob?.cancel()
        tunnelWorkerJob = null
        try {
            activeChannel?.disconnect()
        } catch (_: Exception) {}
        activeChannel = null
        try {
            activeSession?.disconnect()
        } catch (_: Exception) {}
        activeSession = null
    }

    fun stopTunnel(): TunnelConfig {
        stopTunnelInternal()
        val updated = _config.value.copy(
            isEnabled = false,
            status = TunnelStatus.STOPPED,
            lastError = null
        )
        _config.value = updated
        saveConfig(updated)
        return updated
    }

    suspend fun testTunnel(localPort: Int): Pair<Boolean, Long> = withContext(Dispatchers.IO) {
        val start = System.currentTimeMillis()
        val pubUrl = _config.value.publicUrl

        val targetUrl = if (!pubUrl.isNullOrBlank() && (pubUrl.startsWith("http://") || pubUrl.startsWith("https://"))) {
            "$pubUrl/health"
        } else {
            "http://127.0.0.1:$localPort/health"
        }

        try {
            val url = URL(targetUrl)
            val conn = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 8000
                readTimeout = 8000
                requestMethod = "GET"
                setRequestProperty("Accept", "application/json")
                setRequestProperty("User-Agent", "AgentX-Serve/1.0")
            }
            val code = conn.responseCode
            val latency = System.currentTimeMillis() - start
            val success = code in 200..299

            _config.value = _config.value.copy(
                latencyMs = latency,
                status = if (success) TunnelStatus.CONNECTED else TunnelStatus.ERROR,
                lastError = if (!success) "Target gateway returned HTTP $code" else null
            )
            Pair(success, latency)
        } catch (e: Exception) {
            val latency = System.currentTimeMillis() - start
            _config.value = _config.value.copy(
                latencyMs = latency,
                status = TunnelStatus.ERROR,
                lastError = e.message ?: "Failed to connect to $targetUrl"
            )
            Pair(false, latency)
        }
    }

    private fun generateDeterministicTunnelUrl(provider: TunnelProvider, customDomain: String): String {
        val suffix = UUID.randomUUID().toString().take(8)
        return when (provider) {
            TunnelProvider.CLOUDFLARE -> "https://agentx-$suffix.trycloudflare.com"
            TunnelProvider.NGROK -> if (customDomain.isNotBlank()) "https://$customDomain.ngrok-free.app" else "https://agentx-$suffix.ngrok-free.app"
            TunnelProvider.PINGGY -> "https://agentx-$suffix.run.pinggy-free.link"
            TunnelProvider.CUSTOM_RELAY -> if (customDomain.isNotBlank()) customDomain else "https://relay-$suffix.agentx.local"
        }
    }
}
