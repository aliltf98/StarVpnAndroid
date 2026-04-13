package com.iran.vpn

import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.annotation.RequiresApi
import android.app.Notification
import androidx.core.app.NotificationCompat
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.ServiceInfo

// برای اندروید ۱۴ به بالا
private const val CHANNEL_ID = "vpn_service_channel"

class iVpnService : VpnService() {

    private fun createNotificationChannel() {
        val serviceChannel = NotificationChannel(
            CHANNEL_ID,
            "VPN Service Channel",
            NotificationManager.IMPORTANCE_DEFAULT
        )
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(serviceChannel)
    }

    private var vpnInterface: ParcelFileDescriptor? = null

    @RequiresApi(Build.VERSION_CODES.Q)
    override fun onStartCommand(intent: android.content.Intent?, flags: Int, startId: Int): Int {
        createNotificationChannel()

        // وقتی کاربر روی اعلان بزند، برنامه باز می‌شود
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("IranVPN در حال اجراست")
            .setContentText("اتصال ایمن برقرار است...")
            .setSmallIcon(android.R.drawable.ic_dialog_info) // می‌توانید آیکون خود را بگذارید
            .setContentIntent(pendingIntent)
            .build()
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            // برای اندروید ۱۴ به بالا
            startForeground(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            // برای نسخه‌های قدیمی‌تر مثل Note 8 شما
            startForeground(1, notification)
        }

        // شروع سرویس در حالت پیش‌زمینه



        setupVpn()

        vpnInterface?.let { fd ->
            EngineManager.startTun2Socks(this, fd.fd)
        }
        Log.d("VpnService", "سرویس شروع به کار کرد")

        // ۱. ایجاد اینترفیس شبکه

        return START_STICKY
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun setupVpn() {
        val builder = Builder()
        builder.addDisallowedApplication(packageName)
        vpnInterface = builder
            .setSession("IranVPN")
            .addAddress("10.0.0.1", 24)
            .addDnsServer("8.8.8.8")
            .addRoute("0.0.0.0", 0)
            // این خط بسیار حیاتی است (اختیاری برای تست مرورگر)
//            .setHttpProxy(android.net.ProxyInfo.buildDirectProxy("127.0.0.1", 10808))
            .establish()

        Log.d("VpnService", "اینترفیس VPN با موفقیت ساخته شد.")
    }

    override fun onDestroy() {
        super.onDestroy()
        // ۱. خاموش کردن موتورهای خارجی
        EngineManager.stopAll()

        // ۲. بستن اینترفیس شبکه اندروید
        vpnInterface?.close()
        vpnInterface = null

        Log.d("VpnService", "سرویس و موتورها کاملاً متوقف شدند.")
    }

}