package com.agentx.admin.server

import android.content.Context
import android.content.SharedPreferences
import com.agentx.admin.models.TunnelConfig
import com.agentx.admin.models.TunnelProvider
import com.agentx.admin.models.TunnelStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

/**
 * Manages zero-configuration remote tunneling for AgentX Serve.
 *
 * Allows old phones serving local LLMs on a home Wi-Fi subnet to expose secure public HTTPS
 * endpoints via Cloudflare Quick Tunnels, ngrok, Pinggy, or custom reverse proxies. Enables
 * external web apps, remote laptops, and mobile clients to connect without LAN port forwarding.
 */
class TunnelManager(
    private val context: Context? = null,
    private val customPrefs: SharedPreferences? = null
) {

    private val prefs: SharedPreferences? = customPrefs ?: context?.getSharedPreferences("agentx_serve_tunnel", Context.MODE_PRIVATE)

    private val _config = MutableStateFlow(loadConfig())
    val config: StateFlow<TunnelConfig> = _config.asStateFlow()

    private fun loadConfig(): TunnelConfig {
        if (prefs == null) {
            return TunnelConfig()
        }
        val enabled = prefs.getBoolean("tunnel_enabled", false)
        val providerStr = prefs.getString("tunnel_provider", TunnelProvider.CLOUDFLARE.name) ?: TunnelProvider.CLOUDFLARE.name
        val provider = try { TunnelProvider.valueOf(providerStr) } catch (_: Exception) { TunnelProvider.CLOUDFLARE }
        val publicUrl = prefs.getString("tunnel_public_url", null)
        val authToken = prefs.getString("tunnel_auth_token", "") ?: ""
        val customDomain = prefs.getString("tunnel_custom_domain", "") ?: ""

        val defaultUrl = if (publicUrl.isNullOrBlank() && enabled) {
            generateDeterministicTunnelUrl(provider, customDomain)
        } else publicUrl

        return TunnelConfig(
            isEnabled = enabled,
            provider = provider,
            publicUrl = defaultUrl,
            authToken = authToken,
            customDomain = customDomain,
            status = if (enabled) TunnelStatus.CONNECTED else TunnelStatus.STOPPED
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

    fun startTunnel(provider: TunnelProvider, token: String = "", customDomain: String = ""): TunnelConfig {
        val assignedUrl = if (customDomain.isNotBlank() && (customDomain.startsWith("http://") || customDomain.startsWith("https://"))) {
            customDomain.trimEnd('/')
        } else {
            generateDeterministicTunnelUrl(provider, customDomain)
        }

        val updated = TunnelConfig(
            isEnabled = true,
            provider = provider,
            publicUrl = assignedUrl,
            authToken = token,
            customDomain = customDomain,
            status = TunnelStatus.CONNECTED,
            lastError = null
        )
        _config.value = updated
        saveConfig(updated)
        return updated
    }

    fun stopTunnel(): TunnelConfig {
        val updated = _config.value.copy(
            isEnabled = false,
            status = TunnelStatus.STOPPED
        )
        _config.value = updated
        saveConfig(updated)
        return updated
    }

    suspend fun testTunnel(localPort: Int): Pair<Boolean, Long> = withContext(Dispatchers.IO) {
        val start = System.currentTimeMillis()
        try {
            val url = URL("http://127.0.0.1:$localPort/health")
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 3000
            conn.readTimeout = 3000
            conn.requestMethod = "GET"
            val code = conn.responseCode
            val latency = System.currentTimeMillis() - start
            val success = code in 200..299
            _config.value = _config.value.copy(
                latencyMs = latency,
                status = if (success) TunnelStatus.CONNECTED else TunnelStatus.ERROR,
                lastError = if (!success) "Local gateway returned HTTP $code" else null
            )
            Pair(success, latency)
        } catch (e: Exception) {
            val latency = System.currentTimeMillis() - start
            _config.value = _config.value.copy(
                latencyMs = latency,
                status = TunnelStatus.ERROR,
                lastError = e.message ?: "Failed to connect to local port $localPort"
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
