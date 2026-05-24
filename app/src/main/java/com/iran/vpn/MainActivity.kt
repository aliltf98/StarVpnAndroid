package com.iran.vpn

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.graphics.PorterDuff
import android.net.TrafficStats
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
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
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.navigation.NavigationView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.InetSocketAddress
import java.net.Socket

class MainActivity : ComponentActivity() {
    private lateinit var btnPower: ImageButton
    private lateinit var tvStatus: TextView
    private lateinit var drawerLayout: DrawerLayout
    private lateinit var ivMenu: ImageView
    private lateinit var navView: NavigationView
    private lateinit var tvMainProtocol: TextView
    private lateinit var ivGlobe: ImageView

    // Metrics Layout Views
    private lateinit var tvDataUsage: TextView
    private lateinit var tvVolumeStatus: TextView
    private lateinit var btnReloadUsage: ImageButton

    private var selectedConfig: VpnConfigResponse? = null

    // Live Traffic Trackers
    private val metricsHandler = Handler(Looper.getMainLooper())
    private var metricsRunnable: Runnable? = null

    // 🌟 These now persist across app pauses/resumes to maintain session history
    private var initialTxBytes: Long = 0
    private var initialRxBytes: Long = 0
    private var isTrackingSession: Boolean = false

    private val vpnStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val isConnected = intent?.getBooleanExtra(iVpnService.EXTRA_STATE, false) ?: false
            updateUi(isConnected)

            if (isConnected) {
                // 🌟 ONLY capture the baseline once when the connection first registers as true
                if (!isTrackingSession) {
                    initialTxBytes = TrafficStats.getTotalTxBytes()
                    initialRxBytes = TrafficStats.getTotalRxBytes()
                    isTrackingSession = true
                }
                startMetricsUpdates()
            } else {
                // 🌟 Reset trackers to zero on true disconnection
                isTrackingSession = false
                stopMetricsUpdates()
            }
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

        tvDataUsage = findViewById(R.id.tvDataUsage)
        tvVolumeStatus = findViewById(R.id.tvVolumeStatus)
//        btnReloadUsage = findViewById(R.id.btnReloadUsage)

        val protocolSelector = findViewById<RelativeLayout>(R.id.rlProtocolSelector)
        tvMainProtocol = findViewById(R.id.tvSelectedProtocol)
        ivGlobe = findViewById(R.id.ivGlobe)

        loadCachedSubscriptionData()

        btnPower.setOnClickListener {
            if (iVpnService.isRunning) stopVpn() else startVpn()
        }

//        btnReloadUsage.setOnClickListener {
//            Toast.makeText(this, "بروزرسانی حجم مصرفی...", Toast.LENGTH_SHORT).show()
//            fetchMarzbanSubscriptionData()
//        }

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
        val running = iVpnService.isRunning
        updateUi(running)

        val filter = IntentFilter(iVpnService.ACTION_VPN_STATE_CHANGED)
        registerReceiver(vpnStateReceiver, filter, RECEIVER_NOT_EXPORTED)

        if (running) {
            // If it was already running when the app opened, align our flag state
            if (!isTrackingSession) {
                initialTxBytes = TrafficStats.getTotalTxBytes()
                initialRxBytes = TrafficStats.getTotalRxBytes()
                isTrackingSession = true
            }
            startMetricsUpdates()
        } else {
            stopMetricsUpdates()
        }

        fetchMarzbanSubscriptionData()
    }

    override fun onPause() {
        super.onPause()
        unregisterReceiver(vpnStateReceiver)
        // Stop the UI looper handler to save battery, but leave the byte counts intact!
        metricsRunnable?.let { metricsHandler.removeCallbacks(it) }
    }

    private fun loadCachedSubscriptionData() {
        val dataLimit = SharedPrefsHelper.getLong(this, "data_limit", 0L)
        val remainingBytes = SharedPrefsHelper.getLong(this, "remaining_bytes", 0L)

        if (dataLimit == 0L) {
            tvVolumeStatus.text = "Remaining Volume: Unlimited"
        } else {
            tvVolumeStatus.text = "Remaining Volume: ${formatBytes(remainingBytes)}"
        }
    }

    // --- Live Throughput Traffic Counters ---
    private fun startMetricsUpdates() {
        // Remove any existing loops to prevent double scheduling spikes
        metricsRunnable?.let { metricsHandler.removeCallbacks(it) }

        metricsRunnable = object : Runnable {
            override fun run() {
                val currentTx = TrafficStats.getTotalTxBytes() - initialTxBytes
                val currentRx = TrafficStats.getTotalRxBytes() - initialRxBytes

                // Keep metrics normalized to 0 if system counters slightly fluctuate below baseline
                val displayTx = if (currentTx > 0) currentTx else 0L
                val displayRx = if (currentRx > 0) currentRx else 0L

                tvDataUsage.text = "Data Usage: ↑ ${formatBytes(displayTx)} | ↓ ${formatBytes(displayRx)}"
                metricsHandler.postDelayed(this, 1000)
            }
        }
        metricsHandler.post(metricsRunnable!!)
    }

    private fun stopMetricsUpdates() {
        metricsRunnable?.let { metricsHandler.removeCallbacks(it) }
        tvDataUsage.text = "Data Usage: ↑ 0 KB | ↓ 0 KB"
    }

    private fun formatBytes(bytes: Long): String {
        if (bytes < 1024) return "$bytes B"
        val exp = (Math.log(bytes.toDouble()) / Math.log(1024.0)).toInt()
        val units = arrayOf("KB", "MB", "GB", "TB")
        return String.format("%.2f %s", bytes / Math.pow(1024.0, exp.toDouble()), units[exp - 1])
    }

    // --- Dynamic Network Synchronization API Sync ---
    private fun fetchMarzbanSubscriptionData() {
        val username = SharedPrefsHelper.getString(this, "auth_username", "")
        if (username.isEmpty()) {
            tvVolumeStatus.text = "Remaining Volume: N/A"
            return
        }

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val response = RetrofitClient.instance.getVolumeStatus(username)

                withContext(Dispatchers.Main) {
                    if (response.isSuccessful && response.body() != null) {
                        val volumeData = response.body()!!
                        val dataLimit = volumeData.data_limit
                        val remainingBytes = volumeData.remaining_bytes

                        if (dataLimit == 0L) {
                            tvVolumeStatus.text = "Remaining Volume: Unlimited"
                        } else {
                            tvVolumeStatus.text = "Remaining Volume: ${formatBytes(remainingBytes)}"
                        }

                        SharedPrefsHelper.saveLong(this@MainActivity, "data_limit", dataLimit)
                        SharedPrefsHelper.saveLong(this@MainActivity, "remaining_bytes", remainingBytes)
                    }
                }
            } catch (e: Exception) {
                // Keep values stable if signal fades out
            }
        }
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
            val tvLatency = itemView.findViewById<TextView>(R.id.tvProtocolLatency)

            tvName.text = extractRemark(config.value)

            val iconRes = when {
                config.value.startsWith("vless") -> R.drawable.ic_v2ray
                config.value.startsWith("vmess") -> R.drawable.ic_v2ray
                config.value.startsWith("trojan") -> R.drawable.ic_v2ray
                else -> R.drawable.ic_auto
            }
            ivIcon.setImageResource(iconRes)

            val hostPort = extractHostAndPort(config.value)
            if (hostPort != null) {
                tvLatency.text = "Checking..."
                tvLatency.setTextColor(Color.GRAY)
                measureLatency(hostPort.first, hostPort.second) { ms ->
                    if (ms >= 0) {
                        tvLatency.text = "$ms ms"
                        when {
                            ms < 150 -> tvLatency.setTextColor(Color.parseColor("#4CAF50"))
                            ms < 300 -> tvLatency.setTextColor(Color.parseColor("#FF9800"))
                            else -> tvLatency.setTextColor(Color.parseColor("#F44336"))
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
                fetchMarzbanSubscriptionData()
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

    private fun extractHostAndPort(link: String): Pair<String, Int>? {
        return try {
            val clearPart = link.split("://").last()
            val addressPart = clearPart.split("@").last().split("?").first().split("#").first()
            val host = addressPart.split(":").first()
            val port = addressPart.split(":").last().toInt()
            Pair(host, port)
        } catch (e: Exception) {
            null
        }
    }

    private fun measureLatency(host: String, port: Int, callback: (Long) -> Unit) {
        Thread {
            val startTime = System.currentTimeMillis()
            try {
                val socket = Socket()
                socket.connect(InetSocketAddress(host, port), 2000)
                socket.close()
                val latency = System.currentTimeMillis() - startTime
                runOnUiThread { callback(latency) }
            } catch (e: Exception) {
                runOnUiThread { callback(-1L) }
            }
        }.start()
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
}