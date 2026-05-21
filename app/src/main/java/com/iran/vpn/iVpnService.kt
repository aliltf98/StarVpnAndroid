package com.iran.vpn

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.core.app.NotificationCompat

private const val CHANNEL_ID = "vpn_service_channel"

class iVpnService : VpnService() {

    private var vpnInterface: ParcelFileDescriptor? = null

    private fun createNotificationChannel() {
        val serviceChannel = NotificationChannel(
            CHANNEL_ID, "VPN Service Channel", NotificationManager.IMPORTANCE_DEFAULT
        )
        getSystemService(NotificationManager::class.java).createNotificationChannel(serviceChannel)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        createNotificationChannel()

        val pendingIntent = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("IranVPN Active")
            .setContentText("Secure connection established...")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(pendingIntent)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(1, notification)
        }

        val config = VpnDataManager.pendingConfig
        if (config == null) {
            Log.e("VpnService", "No pending config found, stopping service.")
            stopSelf()
            return START_NOT_STICKY
        }

        setupVpnAndStart(config)
        return START_STICKY
    }
// Inside iVpnService.kt -> Change the setupVpnAndStart method to look like this:

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

            val pfd = vpnInterface ?: return

            Log.d("VpnService", "TUN interface established, fd=${pfd.fd}")

            EngineManager.startEngine(this, config, pfd.fd)
            val fd = pfd.fd
            Log.d("VpnService", "TUN interface established, fd=$fd")

            // Crucial Change: Pass the context, config, and the actual fd integer

        } catch (e: Exception) {
            Log.e("VpnService", "Setup VPN failed: ${e.message}")
            stopSelf()
        }
    }
    override fun onDestroy() {
        EngineManager.stopAll()
        try {
            vpnInterface?.close()
            vpnInterface = null
        } catch (e: Exception) {
            Log.e("VpnService", "Error closing interface: ${e.message}")
        }
        super.onDestroy()
        Log.d("VpnService", "VPN Service destroyed.")
    }
}