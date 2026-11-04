package com.agentx.core.discovery

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class DiscoveredGatewayHub(
    val name: String,
    val host: String,
    val port: Int,
    val url: String = "http://$host:$port"
)

/**
 * Client-side Network Service Discovery (mDNS) manager:
 * Discovers active AgentX Serve model hosting instances on the local Wi-Fi subnet,
 * allowing users to connect to their phone or server with a single tap (Zero-Configuration).
 */
class GatewayNsdDiscovery(private val context: Context) {

    private val nsdManager: NsdManager? by lazy {
        try {
            context.getSystemService(Context.NSD_SERVICE) as? NsdManager
        } catch (_: Exception) {
            null
        }
    }

    private val _discoveredHubs = MutableStateFlow<List<DiscoveredGatewayHub>>(emptyList())
    val discoveredHubs: StateFlow<List<DiscoveredGatewayHub>> = _discoveredHubs.asStateFlow()

    private var discoveryListener: NsdManager.DiscoveryListener? = null
    var isDiscovering: Boolean = false
        private set

    fun startDiscovery() {
        if (isDiscovering || nsdManager == null) return

        discoveryListener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(regType: String) {
                isDiscovering = true
            }

            override fun onServiceFound(service: NsdServiceInfo) {
                if (service.serviceType.contains("_agentx-serve")) {
                    resolveService(service)
                }
            }

            override fun onServiceLost(service: NsdServiceInfo) {
                val current = _discoveredHubs.value.filter { it.name != service.serviceName }
                _discoveredHubs.value = current
            }

            override fun onDiscoveryStopped(serviceType: String) {
                isDiscovering = false
            }

            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                isDiscovering = false
            }

            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
                isDiscovering = false
            }
        }

        try {
            nsdManager?.discoverServices("_agentx-serve._tcp.", NsdManager.PROTOCOL_DNS_SD, discoveryListener)
        } catch (_: Exception) {}
    }

    fun stopDiscovery() {
        if (!isDiscovering || discoveryListener == null) return
        try {
            nsdManager?.stopServiceDiscovery(discoveryListener)
        } catch (_: Exception) {}
        finally {
            isDiscovering = false
            discoveryListener = null
        }
    }

    private fun resolveService(serviceInfo: NsdServiceInfo) {
        val resolverListener = object : NsdManager.ResolveListener {
            override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {}

            override fun onServiceResolved(resolved: NsdServiceInfo) {
                val host = resolved.host?.hostAddress ?: return
                val port = resolved.port
                val hub = DiscoveredGatewayHub(
                    name = resolved.serviceName.ifBlank { "AgentX Serve" },
                    host = host,
                    port = port,
                    url = "http://$host:$port"
                )
                val current = _discoveredHubs.value.filter { it.url != hub.url }
                _discoveredHubs.value = current + hub
            }
        }

        try {
            nsdManager?.resolveService(serviceInfo, resolverListener)
        } catch (_: Exception) {}
    }
}
