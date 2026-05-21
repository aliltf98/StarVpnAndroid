package com.iran.vpn

import android.app.AlertDialog
import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import android.net.VpnService
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.PorterDuff
import android.util.Log
import android.widget.Button
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RelativeLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts


class MainActivity : ComponentActivity() {
    private lateinit var btnPower: ImageButton
    private var isConnected = false
    private lateinit var tvStatus: TextView

    private var selectedConfig: VpnConfigResponse? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val savedConfigs = SharedPrefsHelper.getConfigs(this)
        if (savedConfigs != null) {
            VpnDataManager.currentConfigs = savedConfigs
        }

        setContentView(R.layout.activity_main)

        val drawerLayout = findViewById<androidx.drawerlayout.widget.DrawerLayout>(R.id.drawerLayout)
        val ivMenu = findViewById<ImageView>(R.id.ivMenu)
        val navView = findViewById<com.google.android.material.navigation.NavigationView>(R.id.navView)

        btnPower = findViewById(R.id.btnPower)
        tvStatus = findViewById(R.id.tvConnectStatus)
        val protocolSelector = findViewById<RelativeLayout>(R.id.rlProtocolSelector)

        btnPower.setOnClickListener {
            if (isConnected) stopVpn() else startVpn()
        }

        protocolSelector.setOnClickListener {
            showProtocolDialog()
        }

        ivMenu.setOnClickListener {
            drawerLayout.openDrawer(androidx.core.view.GravityCompat.START)
        }

        navView.setNavigationItemSelectedListener { menuItem ->
            when (menuItem.itemId) {
                R.id.nav_profile -> Toast.makeText(this, "Profile Selected", Toast.LENGTH_SHORT).show()
                R.id.nav_settings -> { }
                R.id.nav_about -> { }
                R.id.nav_exit -> performLogout()
            }
            drawerLayout.closeDrawer(androidx.core.view.GravityCompat.START)
            true
        }
    }

    private fun performLogout() {
        val vpnIntent = Intent(this, iVpnService::class.java)
        stopService(vpnIntent)
        EngineManager.stopAll()
        SharedPrefsHelper.clearPrefs(this)
        VpnDataManager.clearData()

        val intent = Intent(this, SigninActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }

    private fun startVpn() {
        val config = selectedConfig ?: VpnDataManager.currentConfigs.firstOrNull()

        if (config == null) {
            Toast.makeText(this, "لطفاً ابتدا یک کانفیگ انتخاب کنید", Toast.LENGTH_SHORT).show()
            return
        }

        val vlessData = parseVlessUri(config.value)
        if (vlessData == null) {
            Toast.makeText(this, "کانفیگ نامعتبر است", Toast.LENGTH_SHORT).show()
            return
        }

        // ۱. باینری‌ها را استخراج کن
//        EngineManager.extractBinary(this, "xray")
//        EngineManager.extractBinary(this, "tun2socks")

        // ۲. ابتدا اجازه VPN بگیر
        //    بعد از تأیید کاربر، Xray شروع می‌شود و سپس VPN سرویس بالا می‌آید
        askVpnPermission(vlessData)
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
            btnPower.setColorFilter(android.graphics.Color.parseColor("#FF2D55"), PorterDuff.Mode.SRC_IN)
            tvStatus.text = "Disconnect Now"
            tvStatus.setTextColor(android.graphics.Color.parseColor("#FF2D55"))
        } else {
            btnPower.setColorFilter(android.graphics.Color.BLACK, PorterDuff.Mode.SRC_IN)
            tvStatus.text = "Connect Now"
            tvStatus.setTextColor(android.graphics.Color.BLACK)
        }
    }

    // نگه داشتن config برای استفاده بعد از تأیید permission
    private var pendingVlessData: VlessConfig? = null

    private val vpnPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            // Permission granted — start the VpnService
            // iVpnService will set up TUN and start Xray with the fd
            val serviceIntent = Intent(this, iVpnService::class.java)
            startService(serviceIntent)
            updateUi(true)
            pendingVlessData = null
        } else {
            Toast.makeText(this, "VPN permission denied", Toast.LENGTH_SHORT).show()
        }
    }

    private fun askVpnPermission(vlessData: VlessConfig) {
        pendingVlessData = vlessData
        // Store config so iVpnService can access it when it starts
        VpnDataManager.pendingConfig = vlessData

        val intent = VpnService.prepare(this)
        if (intent != null) {
            vpnPermissionLauncher.launch(intent)
        } else {
            // Permission already granted - start service directly
            // iVpnService will start Xray with the tun fd internally
            val serviceIntent = Intent(this, iVpnService::class.java)
            startService(serviceIntent)
            updateUi(true)
            pendingVlessData = null
        }
    }

    private fun showProtocolDialog() {
        val dialog = com.google.android.material.bottomsheet.BottomSheetDialog(this, R.style.BottomSheetDialogTheme)
        val view = layoutInflater.inflate(R.layout.layout_protocol_list, null)

        val container = view.findViewById<LinearLayout>(R.id.containerProtocols)
        val tvMainProtocol = findViewById<TextView>(R.id.tvSelectedProtocol)
        val ivGlobe = findViewById<ImageView>(R.id.ivGlobe)

        val configs = VpnDataManager.currentConfigs

        if (configs.isEmpty()) {
            Toast.makeText(this, "هیچ کانفیگی یافت نشد", Toast.LENGTH_SHORT).show()
            return
        }

        configs.forEach { config ->
            val itemView = layoutInflater.inflate(R.layout.item_protocol_row, container, false)
            val tvName = itemView.findViewById<TextView>(R.id.tvProtocolName)
            val ivIcon = itemView.findViewById<ImageView>(R.id.ivProtocolIcon)

            tvName.text = extractRemark(config.value)

            val iconRes = when {
                config.value.startsWith("vless") -> R.drawable.ic_v2ray
                config.value.startsWith("vmess") -> R.drawable.ic_v2ray
                config.value.startsWith("trojan") -> R.drawable.ic_v2ray
                else -> R.drawable.ic_auto
            }
            ivIcon.setImageResource(iconRes)

            itemView.setOnClickListener {
                selectedConfig = config
                tvMainProtocol.text = tvName.text
                ivGlobe.setImageResource(iconRes)
                dialog.dismiss()
            }

            container.addView(itemView)
        }

        dialog.setContentView(view)
        dialog.show()
    }

    private fun extractRemark(link: String): String {
        return try {
            val remark = link.split("#").last()
            java.net.URLDecoder.decode(remark, "UTF-8")
        } catch (e: Exception) {
            "Server"
        }
    }
}