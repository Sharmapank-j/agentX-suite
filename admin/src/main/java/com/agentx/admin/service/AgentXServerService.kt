package com.agentx.admin.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.wifi.WifiManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.agentx.admin.AgentXAdminApplication
import com.agentx.admin.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class AgentXServerService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.Main + Job())
    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: ACTION_START
        val app = application as AgentXAdminApplication

        when (action) {
            ACTION_START -> {
                val port = intent?.getIntExtra(EXTRA_PORT, 8080) ?: 8080
                acquireLocks()
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        startForeground(
                            NOTIFICATION_ID,
                            buildNotification("Starting server on port $port..."),
                            android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
                        )
                    } else {
                        startForeground(NOTIFICATION_ID, buildNotification("Starting server on port $port..."))
                    }
                } catch (_: Throwable) {
                    try {
                        startForeground(NOTIFICATION_ID, buildNotification("Starting server on port $port..."))
                    } catch (_: Throwable) {}
                }

                serviceScope.launch {
                    val started = app.server.start(port)
                    if (started) {
                        val status = app.server.telemetry.value
                        updateNotification("Running on http://${status.ipAddress}:${status.port}")
                        app.nsdHelper.registerService(status.port)
                    } else {
                        updateNotification("Failed to bind port $port")
                    }
                }
            }
            ACTION_STOP -> {
                serviceScope.launch {
                    app.nsdHelper.unregisterService()
                    app.server.stop()
                    releaseLocks()
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                }
            }
        }

        return START_STICKY
    }

    private fun acquireLocks() {
        try {
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "AgentXAdmin::ServerWakeLock").apply {
                acquire(12 * 60 * 60 * 1000L) // 12 hours max
            }

            val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            val wifiMode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                WifiManager.WIFI_MODE_FULL_LOW_LATENCY
            } else {
                @Suppress("DEPRECATION")
                WifiManager.WIFI_MODE_FULL_HIGH_PERF
            }
            wifiLock = wifiManager.createWifiLock(wifiMode, "AgentXAdmin::ServerWifiLock").apply {
                acquire()
            }
        } catch (_: Exception) {}
    }

    private fun releaseLocks() {
        try {
            wakeLock?.let { if (it.isHeld) it.release() }
            wakeLock = null
            wifiLock?.let { if (it.isHeld) it.release() }
            wifiLock = null
        } catch (_: Exception) {}
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "AgentX Edge Server Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps the AgentX Serve HTTP server active for client devices."
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(contentText: String): Notification {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("AgentX Serve — ONLINE")
            .setContentText(contentText)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(text: String) {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, buildNotification(text))
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        try {
            (application as? AgentXAdminApplication)?.nsdHelper?.unregisterService()
        } catch (_: Exception) {}
        releaseLocks()
        super.onDestroy()
    }

    companion object {
        const val CHANNEL_ID = "agentx_server_channel"
        const val NOTIFICATION_ID = 2024
        const val ACTION_START = "com.agentx.admin.START"
        const val ACTION_STOP = "com.agentx.admin.STOP"
        const val EXTRA_PORT = "com.agentx.admin.PORT"
    }
}
