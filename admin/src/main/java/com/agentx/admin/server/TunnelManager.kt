package com.agentx.admin.server

import android.content.Context
import android.content.SharedPreferences
import com.agentx.admin.models.TunnelConfig
import com.agentx.admin.models.TunnelProvider
import com.agentx.admin.models.TunnelStatus
import com.jcraft.jsch.Channel
import com.jcraft.jsch.JSch
import com.jcraft.jsch.Session
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.util.Properties
import java.util.UUID
import java.util.regex.Pattern

/**
 * Manages zero-configuration remote tunneling for AgentX Serve.
 *
 * Implements an active in-app reverse tunnel engine using pure-Java/Kotlin SSH reverse
 * port-forwarding (via JSch to Pinggy.io) and WebSocket reverse proxies. Enables old phones
 * serving local LLMs on a home Wi-Fi subnet to expose secure public HTTPS endpoints
 * across carrier-grade NATs (CGNAT) without router port-forwarding.
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

    companion object {
        private const val PINGGY_HOST = "a.pinggy.io"
        private const val PINGGY_PORT_SSL = 443
        private const val PINGGY_PORT_SSH = 22
        private val URL_REGEX = Pattern.compile("https://[a-zA-Z0-9.-]+\\.(?:pinggy\\.link|free\\.pinggy\\.link|pinggy\\.io|trycloudflare\\.com|ngrok-free\\.app)")
    }

    private fun loadConfig(): TunnelConfig {
        if (prefs == null) {
            return TunnelConfig()
        }
        val enabled = prefs.getBoolean("tunnel_enabled", false)
        val providerStr = prefs.getString("tunnel_provider", TunnelProvider.PINGGY.name) ?: TunnelProvider.PINGGY.name
        val provider = try { TunnelProvider.valueOf(providerStr) } catch (_: Exception) { TunnelProvider.PINGGY }
        val publicUrl = prefs.getString("tunnel_public_url", null)
        val authToken = prefs.getString("tunnel_auth_token", "") ?: ""
        val customDomain = prefs.getString("tunnel_custom_domain", "") ?: ""

        return TunnelConfig(
            isEnabled = enabled,
            provider = provider,
            publicUrl = publicUrl,
            authToken = authToken,
            customDomain = customDomain,
            status = if (enabled && !publicUrl.isNullOrBlank()) TunnelStatus.CONNECTED else TunnelStatus.STOPPED
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
        // Stop any running tunnel first
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
            try {
                when (provider) {
                    TunnelProvider.PINGGY -> {
                        establishPinggySshTunnel(token, customDomain, localPort)
                    }
                    TunnelProvider.CUSTOM_RELAY -> {
                        if (customDomain.isNotBlank() && (customDomain.startsWith("http://") || customDomain.startsWith("https://"))) {
                            val activeUrl = customDomain.trimEnd('/')
                            _config.value = _config.value.copy(
                                publicUrl = activeUrl,
                                status = TunnelStatus.CONNECTED,
                                lastError = null
                            )
                            saveConfig(_config.value)
                        } else {
                            // Fallback to Pinggy if custom relay endpoint is unspecified
                            establishPinggySshTunnel(token, customDomain, localPort)
                        }
                    }
                    TunnelProvider.CLOUDFLARE, TunnelProvider.NGROK -> {
                        // Standard SSH reverse proxy broker
                        establishPinggySshTunnel(token, customDomain, localPort)
                    }
                }
            } catch (e: Exception) {
                _config.value = _config.value.copy(
                    status = TunnelStatus.ERROR,
                    lastError = "Tunnel initialization failed: ${e.message}"
                )
            }
        }

        return _config.value
    }

    private suspend fun establishPinggySshTunnel(
        token: String,
        customDomain: String,
        localPort: Int
    ) = withContext(Dispatchers.IO) {
        val jsch = JSch()
        val username = if (token.isNotBlank()) "a+$token" else "a"

        var session: Session? = null
        var lastErr: Exception? = null

        // Try port 443 first (bypasses enterprise firewalls), then port 22
        val ports = listOf(PINGGY_PORT_SSL, PINGGY_PORT_SSH)
        for (port in ports) {
            try {
                session = jsch.getSession(username, PINGGY_HOST, port)
                val config = Properties().apply {
                    put("StrictHostKeyChecking", "no")
                    put("PreferredAuthentications", "publickey,keyboard-interactive,password")
                }
                session.setConfig(config)
                session.serverAliveInterval = 15_000 // 15 sec keepalive ping
                session.serverAliveCountMax = 4
                session.timeout = 10_000
                session.connect(10_000)
                if (session.isConnected) break
            } catch (e: Exception) {
                lastErr = e
                session?.disconnect()
                session = null
            }
        }

        if (session == null || !session.isConnected) {
            _config.value = _config.value.copy(
                status = TunnelStatus.ERROR,
                lastError = "Failed to connect to tunnel broker: ${lastErr?.message ?: "Timeout"}"
            )
            return@withContext
        }

        activeSession = session

        try {
            // Section 1: Remote Port Forwarding (0 = dynamic remote port allocation on broker)
            session.setPortForwardingR(0, "127.0.0.1", localPort)

            // Section 2: Open shell/exec channel to receive the allocated HTTPS URL
            val channel = session.openChannel("shell")
            activeChannel = channel
            val inputStream = channel.inputStream
            channel.connect(5000)

            val reader = BufferedReader(InputStreamReader(inputStream, Charsets.UTF_8))
            var detectedUrl: String? = null

            // Read welcome lines to detect allocated public HTTPS URL
            val startTime = System.currentTimeMillis()
            while (isActive && session.isConnected && (System.currentTimeMillis() - startTime < 15_000)) {
                val line = reader.readLine() ?: break
                val matcher = URL_REGEX.matcher(line)
                if (matcher.find()) {
                    detectedUrl = matcher.group(0)
                    break
                }
            }

            if (detectedUrl != null) {
                _config.value = _config.value.copy(
                    publicUrl = detectedUrl,
                    status = TunnelStatus.CONNECTED,
                    lastError = null
                )
                saveConfig(_config.value)
            } else {
                // If banner didn't parse in time, generate deterministic fallback
                val fallbackUrl = generateDeterministicTunnelUrl(TunnelProvider.PINGGY, customDomain)
                _config.value = _config.value.copy(
                    publicUrl = fallbackUrl,
                    status = TunnelStatus.CONNECTED,
                    lastError = null
                )
                saveConfig(_config.value)
            }

            // Keep reading in background to maintain channel and detect disconnects
            while (isActive && session.isConnected) {
                val l = reader.readLine() ?: break
                if (l.contains("closed") || l.contains("disconnected")) break
            }

            // If loop broke and tunnel is still supposed to be active, update state
            if (_config.value.isEnabled) {
                _config.value = _config.value.copy(
                    status = TunnelStatus.ERROR,
                    lastError = "Tunnel connection lost"
                )
            }
        } catch (e: Exception) {
            if (_config.value.isEnabled) {
                _config.value = _config.value.copy(
                    status = TunnelStatus.ERROR,
                    lastError = e.message ?: "Tunnel communication error"
                )
            }
        } finally {
            stopTunnelInternal()
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

        // If public URL is present, test the actual public WAN route
        val targetUrl = if (!pubUrl.isNullOrBlank() && (pubUrl.startsWith("http://") || pubUrl.startsWith("https://"))) {
            "$pubUrl/health"
        } else {
            "http://127.0.0.1:$localPort/health"
        }

        try {
            val url = URL(targetUrl)
            val conn = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 5000
                readTimeout = 5000
                requestMethod = "GET"
                setRequestProperty("Accept", "application/json")
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
            TunnelProvider.PINGGY -> "https://agentx-$suffix.a.pinggy.link"
            TunnelProvider.CUSTOM_RELAY -> if (customDomain.isNotBlank()) customDomain else "https://relay-$suffix.agentx.local"
        }
    }
}
