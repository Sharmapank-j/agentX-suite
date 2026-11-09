package com.agentx.core.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Monitors device network state and interface transitions (Wi-Fi <-> Cellular <-> Offline).
 * Enables instant reconnection and endpoint re-negotiation when the phone changes network environments.
 */
class NetworkConnectivityMonitor(context: Context) {

    data class NetworkState(
        val isConnected: Boolean = false,
        val isWifi: Boolean = false,
        val isCellular: Boolean = false,
        val isMetered: Boolean = false,
        val timestampMs: Long = System.currentTimeMillis()
    )

    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    private val _networkState = MutableStateFlow(getCurrentState())
    val networkState: StateFlow<NetworkState> = _networkState.asStateFlow()

    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            updateState()
        }

        override fun onLost(network: Network) {
            updateState()
        }

        override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
            updateState()
        }
    }

    init {
        try {
            val request = NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()
            connectivityManager.registerNetworkCallback(request, callback)
        } catch (_: Exception) {}
    }

    private fun updateState() {
        _networkState.value = getCurrentState()
    }

    private fun getCurrentState(): NetworkState {
        val activeNetwork = connectivityManager.activeNetwork ?: return NetworkState()
        val caps = connectivityManager.getNetworkCapabilities(activeNetwork) ?: return NetworkState()

        val hasInternet = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        val isWifi = caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
        val isCellular = caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
        val isMetered = !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)

        return NetworkState(
            isConnected = hasInternet,
            isWifi = isWifi,
            isCellular = isCellular,
            isMetered = isMetered,
            timestampMs = System.currentTimeMillis()
        )
    }

    fun unregister() {
        try {
            connectivityManager.unregisterNetworkCallback(callback)
        } catch (_: Exception) {}
    }
}
