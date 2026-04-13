package com.iran.vpn

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.iran.vpn.ui.theme.IranVPNTheme
import android.net.VpnService
import android.content.Intent
import android.util.Log
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

class MainActivity : ComponentActivity() {
    @Composable
    fun VpnScreen(onStart: () -> Unit, onStop: () -> Unit) {
        var isConnected by remember { mutableStateOf(false) }

        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Button(
                onClick = {
                    if (isConnected) onStop() else onStart()
                    isConnected = !isConnected
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isConnected) Color.Red else Color.Green
                )
            ) {
                Text(if (isConnected) "قطع اتصال" else "اتصال به سرور")
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(text = if (isConnected) "وضعیت: متصل" else "وضعیت: قطع")
        }
    }
    private val vpnPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            // کاربر اجازه داد! حالا سرویس را روشن کن
            val intent = Intent(this, iVpnService::class.java)
            startService(intent)
        } else {
            Log.e("MainActivity", "کاربر اجازه دسترسی به VPN را نداد.")
        }
    }
    private fun askVpnPermission() {
        val intent = VpnService.prepare(this)
        if (intent != null) {
            vpnPermissionLauncher.launch(intent)
        } else {
            // قبلاً اجازه گرفته شده، مستقیماً سرویس را روشن کن
            val serviceIntent = Intent(this, iVpnService::class.java)
            startService(serviceIntent)
        }
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        askVpnPermission()
        val engineFile = EngineManager.extractEngine(this)
        if (engineFile != null) {
            println("پرونده با کامیابی آماده شد در نشانی: ${engineFile.absolutePath}")
        }
        val testConfig = VlessConfig(
            remark = "Test Server",
            address = "1.2.3.4", // آی‌پی سرور خودتان
            port = 443,
            uuid = "YOUR-UUID-HERE",
            sni = "google.com"
        )

        EngineManager.startEngine(this, testConfig)
        enableEdgeToEdge()
        setContent {
            IranVPNTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Greeting(
                        name = "Android",
                        modifier = Modifier.padding(innerPadding)
                    )
                }

            }
            VpnScreen(
                onStart = {
                    // ۱. کپی فایل‌ها (اگر قبلاً نشده باشد)
                    EngineManager.extractEngine(this)
                    // ۲. روشن کردن هسته Xray
                    EngineManager.startEngine(this, testConfig)
                    // ۳. درخواست اجازه و شروع سرویس (که منجر به اجرای tun2socks می‌شود)
                    askVpnPermission()
                },
                onStop = {
                    // توقف همه چیز
                    val intent = Intent(this, iVpnService::class.java)
                    stopService(intent)
                    EngineManager.stopAll()
                }
            )
        }
    }
}

@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Text(
        text = "Hello $name!",
        modifier = modifier
    )
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    IranVPNTheme {
        Greeting("Android")
    }
}