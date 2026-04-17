package com.iran.vpn

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import android.net.VpnService
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.PorterDuff
import android.widget.Button
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.RelativeLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts


class MainActivity : ComponentActivity() {
    private lateinit var btnPower: ImageButton
    private var isConnected = false
    private lateinit var btnToggle: Button
    private lateinit var tvStatus: TextView

    // متغیر کانفیگ (همان تستی که خودتان داشتید)
    private val testConfig = VlessConfig(
        remark = "Test Server",
        address = "1.2.3.4",
        port = 443,
        uuid = "YOUR-UUID-HERE",
        sni = "google.com"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // ۱. متصل کردن به فایل XML
        setContentView(R.layout.activity_main)

        // ۲. پیدا کردن ویوها
        btnPower = findViewById(R.id.btnPower)
        tvStatus = findViewById(R.id.tvConnectStatus)
        // ۳. اکشن دکمه
        btnPower.setOnClickListener {
            if (isConnected) stopVpn() else startVpn()
        }
        val protocolSelector = findViewById<RelativeLayout>(R.id.rlProtocolSelector)
        protocolSelector.setOnClickListener {
            showProtocolDialog()
        }
    }

    private fun startVpn() {
        // استخراج انجین و روشن کردن هسته
        EngineManager.extractEngine(this)
        EngineManager.startEngine(this, testConfig)

        // درخواست اجازه VPN
        askVpnPermission()

        // تغییر ظاهر دکمه
        updateUi(true)
    }

    private fun stopVpn() {
        val intent = Intent(this, iVpnService::class.java)
        stopService(intent)
        EngineManager.stopAll()

        updateUi(false)
    }

    private fun updateUi(connected: Boolean) {
        isConnected = connected
        if (connected) {
            // تغییر رنگ آیکون به سیاه (وقتی روشن است)
            btnPower.setColorFilter((android.graphics.Color.parseColor("#FF2D55")), PorterDuff.Mode.SRC_IN)
            tvStatus.text = "Disconnect Now"
            tvStatus.setTextColor(android.graphics.Color.parseColor("#FF2D55"))
        } else {
            // تغییر رنگ آیکون به صورتی (وقتی خاموش است - طبق تصویر)
            btnPower.setColorFilter(android.graphics.Color.BLACK, PorterDuff.Mode.SRC_IN)
            tvStatus.text = "Connect Now"
            tvStatus.setTextColor(android.graphics.Color.BLACK)
        }
    }

    // کدهای مربوط به vpnPermissionLauncher و askVpnPermission بدون تغییر باقی می‌مانند...
    private val vpnPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val intent = Intent(this, iVpnService::class.java)
            startService(intent)
        }
    }

    private fun askVpnPermission() {
        val intent = VpnService.prepare(this)
        if (intent != null) {
            vpnPermissionLauncher.launch(intent)
        } else {
            val serviceIntent = Intent(this, iVpnService::class.java)
            startService(serviceIntent)
        }
    }

    private fun showProtocolDialog() {
        val dialog = com.google.android.material.bottomsheet.BottomSheetDialog(this, R.style.BottomSheetDialogTheme)
        val view = layoutInflater.inflate(R.layout.layout_protocol_list, null)

        // پیدا کردن ویوهای صفحه اصلی که قرار است تغییر کنند
        val tvMainProtocol = findViewById<TextView>(R.id.tvSelectedProtocol)
        val ivGlobe = findViewById<ImageView>(R.id.ivGlobe) // آیکون کره زمین

        val protocolOptions = listOf(
            Triple(R.id.tvAuto, "Auto", R.drawable.ic_auto),
            Triple(R.id.tvVlessTcp, "Vless Tcp", R.drawable.ic_v2ray),
            Triple(R.id.tvVlessWsTls, "Vless Ws Tls", R.drawable.ic_v2ray),
            Triple(R.id.tvVlessWsNoTls, "Vless Ws no-Tls", R.drawable.ic_v2ray),
            Triple(R.id.tvWireguard, "Wireguard", R.drawable.ic_wiregaurd), // یا آیکون مخصوص خودش
            Triple(R.id.tvOpenVpn, "openVPN", R.drawable.openvpn_icon)
        )

        protocolOptions.forEach { (viewId, name, iconRes) ->
            view.findViewById<TextView>(viewId).setOnClickListener {
                // ۱. تغییر متن در نوار پایین
                tvMainProtocol.text = name

                // ۲. جایگزین کردن عکس ivGlobe با آیکون انتخابی
                ivGlobe.setImageResource(iconRes)

                // ۳. بستن دیالوگ
                dialog.dismiss()
            }
        }

        dialog.setContentView(view)
        dialog.show()
    }
}