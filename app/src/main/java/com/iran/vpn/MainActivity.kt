package com.iran.vpn

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.graphics.PorterDuff
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RelativeLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.graphics.toColorInt
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.navigation.NavigationView

class MainActivity : ComponentActivity() {
    private lateinit var btnPower: ImageButton
    private lateinit var tvStatus: TextView
    private lateinit var drawerLayout: DrawerLayout
    private lateinit var ivMenu: ImageView
    private lateinit var navView: NavigationView
    private lateinit var tvMainProtocol: TextView
    private lateinit var ivGlobe: ImageView

    private var selectedConfig: VpnConfigResponse? = null

    // Synchronize state changes triggered from notifications or network switches
    private val vpnStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val isConnected = intent?.getBooleanExtra(iVpnService.EXTRA_STATE, false) ?: false
            updateUi(isConnected)
        }
    }

    private val vpnPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            executeStartService()
        } else {
            Toast.makeText(this, "VPN permission denied", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val savedConfigs = SharedPrefsHelper.getConfigs(this)
        if (savedConfigs != null) {
            VpnDataManager.currentConfigs = savedConfigs
        }

        setContentView(R.layout.activity_main)

        drawerLayout = findViewById(R.id.drawerLayout)
        ivMenu = findViewById(R.id.ivMenu)
        navView = findViewById(R.id.navView)
        btnPower = findViewById(R.id.btnPower)
        tvStatus = findViewById(R.id.tvConnectStatus)

        val protocolSelector = findViewById<RelativeLayout>(R.id.rlProtocolSelector)
        tvMainProtocol = findViewById(R.id.tvSelectedProtocol)
        ivGlobe = findViewById(R.id.ivGlobe)

        btnPower.setOnClickListener {
            if (iVpnService.isRunning) {
                stopVpn()
            } else {
                startVpn()
            }
        }

        protocolSelector.setOnClickListener {
            if (iVpnService.isRunning) {
                Toast.makeText(this, "Please disconnect VPN before changing servers", Toast.LENGTH_SHORT).show()
            } else {
                showProtocolDialog()
            }
        }

        ivMenu.setOnClickListener {
            drawerLayout.openDrawer(GravityCompat.START)
        }

        navView.setNavigationItemSelectedListener { menuItem ->
            when (menuItem.itemId) {
                R.id.nav_profile -> Toast.makeText(this, "Profile Selected", Toast.LENGTH_SHORT).show()
                R.id.nav_settings -> { }
                R.id.nav_about -> { }
                R.id.nav_exit -> performLogout()
            }
            drawerLayout.closeDrawer(GravityCompat.START)
            true
        }
    }

    override fun onResume() {
        super.onResume()
        // Instantly sync layout state with background service presence on reopen
        updateUi(iVpnService.isRunning)

        val filter = IntentFilter(iVpnService.ACTION_VPN_STATE_CHANGED)

        // 🌟 This single line replaces the entire if/else block,
        // explicitly protects your app's broadcast, and clears the compiler error.
        registerReceiver(vpnStateReceiver, filter, RECEIVER_NOT_EXPORTED)
    }

    override fun onPause() {
        super.onPause()
        unregisterReceiver(vpnStateReceiver)
    }

    private fun performLogout() {
        stopVpn()
        SharedPrefsHelper.clearPrefs(this)
        VpnDataManager.clearData()

        val intent = Intent(this, SigninActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        startActivity(intent)
        finish()
    }

    private fun startVpn() {
        val config = selectedConfig ?: VpnDataManager.currentConfigs.firstOrNull()

        if (config == null) {
            Toast.makeText(this, "Please select a configuration first", Toast.LENGTH_SHORT).show()
            return
        }

        val vlessData = parseVlessUri(config.value)
        if (vlessData == null) {
            Toast.makeText(this, "Invalid configuration structure", Toast.LENGTH_SHORT).show()
            return
        }

        askVpnPermission(vlessData)
    }

    private fun stopVpn() {
        val intent = Intent(this, iVpnService::class.java).apply {
            action = iVpnService.ACTION_STOP_VPN
        }
        startService(intent)
    }

    private fun askVpnPermission(vlessData: VlessConfig) {
        VpnDataManager.pendingConfig = vlessData

        val intent = VpnService.prepare(this)
        if (intent != null) {
            vpnPermissionLauncher.launch(intent)
        } else {
            executeStartService()
        }
    }

    private fun executeStartService() {
        val serviceIntent = Intent(this, iVpnService::class.java).apply {
            action = iVpnService.ACTION_START_VPN
        }
        startForegroundService(serviceIntent)
    }

    private fun updateUi(connected: Boolean) {
        if (connected) {
            btnPower.setColorFilter("#FF2D55".toColorInt(), PorterDuff.Mode.SRC_IN)
            tvStatus.text = "Disconnect Now"
            tvStatus.setTextColor("#FF2D55".toColorInt())
        } else {
            btnPower.setColorFilter(Color.BLACK, PorterDuff.Mode.SRC_IN)
            tvStatus.text = "Connect Now"
            tvStatus.setTextColor(Color.BLACK)
        }
    }

    private fun showProtocolDialog() {
        val dialog = BottomSheetDialog(this, R.style.BottomSheetDialogTheme)
        val view = layoutInflater.inflate(R.layout.layout_protocol_list, null)

        val container = view.findViewById<LinearLayout>(R.id.containerProtocols)
        val configs = VpnDataManager.currentConfigs

        if (configs.isEmpty()) {
            Toast.makeText(this, "No configurations found", Toast.LENGTH_SHORT).show()
            return
        }

        configs.forEach { config ->
            val itemView = layoutInflater.inflate(R.layout.item_protocol_row, container, false)
            val tvName = itemView.findViewById<TextView>(R.id.tvProtocolName)
            val ivIcon = itemView.findViewById<ImageView>(R.id.ivProtocolIcon)
            val tvLatency = itemView.findViewById<TextView>(R.id.tvProtocolLatency) // 🌟 Reference new View

            tvName.text = extractRemark(config.value)

            val iconRes = when {
                config.value.startsWith("vless") -> R.drawable.ic_v2ray
                config.value.startsWith("vmess") -> R.drawable.ic_v2ray
                config.value.startsWith("trojan") -> R.drawable.ic_v2ray
                else -> R.drawable.ic_auto
            }
            ivIcon.setImageResource(iconRes)

            // 🌟 Start Latency Test for this specific row configuration
            val hostPort = extractHostAndPort(config.value)
            if (hostPort != null) {
                tvLatency.text = "Checking..."
                tvLatency.setTextColor(Color.GRAY)

                measureLatency(hostPort.first, hostPort.second) { ms ->
                    if (ms >= 0) {
                        tvLatency.text = "$ms ms"
                        // Color code your latency for visual appeal
                        when {
                            ms < 150 -> tvLatency.setTextColor(Color.parseColor("#4CAF50")) // Green
                            ms < 300 -> tvLatency.setTextColor(Color.parseColor("#FF9800")) // Orange
                            else -> tvLatency.setTextColor(Color.parseColor("#F44336"))     // Red
                        }
                    } else {
                        tvLatency.text = "Timed out"
                        tvLatency.setTextColor(Color.RED)
                    }
                }
            } else {
                tvLatency.text = "N/A"
            }

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
    // Helper function to extract host domain/IP and port from your VLESS config data structure
    private fun extractHostAndPort(link: String): Pair<String, Int>? {
        return try {
            // Simple extraction fallback assuming standard URI format: vless://uuid@host:port
            val clearPart = link.split("://").last()
            val addressPart = clearPart.split("@").last().split("?").first().split("#").first()
            val host = addressPart.split(":").first()
            val port = addressPart.split(":").last().toInt()
            Pair(host, port)
        } catch (e: Exception) {
            null
        }
    }

    // Network execution utility measuring socket response times
    private fun measureLatency(host: String, port: Int, callback: (Long) -> Unit) {
        Thread {
            val startTime = System.currentTimeMillis()
            try {
                val socket = java.net.Socket()
                // Try to connect with a 2-second strict boundary timeout
                socket.connect(java.net.InetSocketAddress(host, port), 2000)
                socket.close()
                val latency = System.currentTimeMillis() - startTime

                // Post result back on the Main UI thread
                runOnUiThread { callback(latency) }
            } catch (e: Exception) {
                runOnUiThread { callback(-1L) } // -1 indicates unreachable server
            }
        }.start()
    }



}