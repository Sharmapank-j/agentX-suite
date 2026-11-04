package com.agentx.admin.server

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import android.util.Log

/**
 * Broadcasts the AgentX Serve instance on the local Wi-Fi network using Android's NsdManager (mDNS).
 * This enables zero-configuration auto-discovery by client devices (:app and :medical) without manual IP entry.
 */
class AgentXNsdHelper(private val context: Context) {

    private val nsdManager: NsdManager? by lazy {
        try {
            context.getSystemService(Context.NSD_SERVICE) as? NsdManager
        } catch (_: Exception) {
            null
        }
    }

    private var registrationListener: NsdManager.RegistrationListener? = null
    var isRegistered: Boolean = false
        private set

    fun registerService(port: Int, customName: String? = null) {
        if (isRegistered || nsdManager == null) return

        val deviceName = customName ?: "AgentX-Serve-${Build.MODEL.replace(" ", "-")}"
        val serviceInfo = NsdServiceInfo().apply {
            serviceName = deviceName
            serviceType = "_agentx-serve._tcp."
            setPort(port)
        }

        registrationListener = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(registeredService: NsdServiceInfo) {
                isRegistered = true
                Log.i("AgentXNsdHelper", "Service registered successfully: ${registeredService.serviceName} on port $port")
            }

            override fun onRegistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                isRegistered = false
                Log.w("AgentXNsdHelper", "Service registration failed with errorCode: $errorCode")
            }

            override fun onServiceUnregistered(arg0: NsdServiceInfo) {
                isRegistered = false
                Log.i("AgentXNsdHelper", "Service unregistered successfully")
            }

            override fun onUnregistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                isRegistered = false
                Log.w("AgentXNsdHelper", "Service unregistration failed with errorCode: $errorCode")
            }
        }

        try {
            nsdManager?.registerService(serviceInfo, NsdManager.PROTOCOL_DNS_SD, registrationListener)
        } catch (e: Exception) {
            Log.e("AgentXNsdHelper", "Failed to register mDNS service: ${e.message}")
        }
    }

    fun unregisterService() {
        if (!isRegistered || registrationListener == null) return
        try {
            nsdManager?.unregisterService(registrationListener)
        } catch (e: Exception) {
            Log.w("AgentXNsdHelper", "Error unregistering NSD service: ${e.message}")
        } finally {
            isRegistered = false
            registrationListener = null
        }
    }
}
