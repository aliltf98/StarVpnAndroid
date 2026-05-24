package com.iran.vpn

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.core.app.NotificationCompat

class iVpnService : VpnService() {

    private var vpnInterface: ParcelFileDescriptor? = null

    companion object {
        var isRunning: Boolean = false
            private set

        const val ACTION_VPN_STATE_CHANGED = "com.iran.vpn.ACTION_VPN_STATE_CHANGED"
        const val EXTRA_STATE = "vpn_state"

        const val ACTION_START_VPN = "com.iran.vpn.START"
        const val ACTION_STOP_VPN = "com.iran.vpn.STOP"

        private const val NOTIFICATION_ID = 4125
        private const val CHANNEL_ID = "vpn_service_channel"
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action

        if (action == ACTION_STOP_VPN) {
            stopVpnService()
            return START_NOT_STICKY
        }

        if (action == ACTION_START_VPN) {
            val config = VpnDataManager.pendingConfig
            if (config != null) {
                isRunning = true
                broadcastState(true)

                startForeground(NOTIFICATION_ID, createNotification())
                setupVpnAndStart(config)
            } else {
                Log.e("iVpnService", "Failed to start: VlessConfig is null")
                stopSelf()
            }
        }

        return START_STICKY
    }

    private fun setupVpnAndStart(config: VlessConfig) {
        try {
            vpnInterface = Builder()
                .setSession(packageName)
                .addAddress("10.0.0.2", 24)
                .addRoute("0.0.0.0", 0)
                .addRoute("::", 0)
                .addDnsServer("1.1.1.1")
                .addDnsServer("8.8.8.8")
                .setMtu(1500)
                .setBlocking(true)
                .addDisallowedApplication(packageName)
                .establish()

            val pfd = vpnInterface ?: run {
                Log.e("iVpnService", "TUN interface creation failed")
                stopVpnService()
                return
            }

            Log.d("iVpnService", "TUN established on fd=${pfd.fd}")
            EngineManager.startEngine(this, config, pfd.fd)

        } catch (e: Exception) {
            Log.e("iVpnService", "VPN pipeline routing failed: ${e.message}")
            stopVpnService()
        }
    }

    private fun stopVpnService() {
        Log.d("iVpnService", "Tearing down connections...")
        try {
            EngineManager.stopAll()

            vpnInterface?.close()
            vpnInterface = null

            isRunning = false
            VpnDataManager.pendingConfig = null
            broadcastState(false)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                stopForeground(STOP_FOREGROUND_REMOVE)
            } else {
                @Suppress("DEPRECATION")
                stopForeground(true)
            }
            stopSelf()

        } catch (e: Exception) {
            Log.e("iVpnService", "Error during cleanup execution: ${e.message}")
        }
    }

    private fun broadcastState(connected: Boolean) {
        val intent = Intent(ACTION_VPN_STATE_CHANGED).apply {
            putExtra(EXTRA_STATE, connected)
            setPackage(packageName)
        }
        sendBroadcast(intent)
    }

    private fun createNotification(): Notification {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "VPN Connection",
                NotificationManager.IMPORTANCE_LOW
            )
            manager.createNotificationChannel(channel)
        }

        val openAppIntent = Intent(this, MainActivity::class.java)
        val openAppPendingIntent = PendingIntent.getActivity(
            this, 0, openAppIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val stopIntent = Intent(this, iVpnService::class.java).apply {
            action = ACTION_STOP_VPN
        }
        val stopPendingIntent = PendingIntent.getService(
            this, 1, stopIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Star VPN")
            .setContentText("Protected connection active")
            .setSmallIcon(android.R.drawable.ic_menu_share)
            .setContentIntent(openAppPendingIntent)
            .setOngoing(true)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Disconnect", stopPendingIntent)
            .build()
    }

    override fun onRevoke() {
        stopVpnService()
        super.onRevoke()
    }

    override fun onDestroy() {
        stopVpnService()
        super.onDestroy()
    }
}