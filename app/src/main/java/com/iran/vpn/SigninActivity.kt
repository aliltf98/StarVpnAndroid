package com.iran.vpn

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

class SigninActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_signin)

        val btnSubmit = findViewById<Button>(R.id.btnSubmit)
        val loading = findViewById<ProgressBar>(R.id.loadingCircle)

        btnSubmit.setOnClickListener {
            loading.visibility = View.VISIBLE
            btnSubmit.isEnabled = false
            performLogin()
        }
    }

    @SuppressLint("HardwareIds")
    private fun performLogin() {
        val btnSubmit = findViewById<Button>(R.id.btnSubmit)
        val loading = findViewById<ProgressBar>(R.id.loadingCircle)
        val username = findViewById<EditText>(R.id.Username).text.toString()
        val password = findViewById<EditText>(R.id.Password).text.toString()

        // Extract device information safely
        val androidId = android.provider.Settings.Secure.getString(contentResolver, android.provider.Settings.Secure.ANDROID_ID)
        val modelName = "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}"

        val loginRequest = SigninRequest(
            username = username,
            password = password,
            device_id = androidId,
            model_name = modelName
        )

        lifecycleScope.launch {
            try {
                val response = RetrofitClient.instance.loginUser(loginRequest)

                // 🌟 Smart Null-Safety check fixes "Assignment type mismatch"
                // Inside your SigninActivity lifecycleScope launch block:
                if (response.isSuccessful && response.body() != null) {
                    val authData = response.body()!!
                    // Save token securely
                    SharedPrefsHelper.saveToken(this@SigninActivity, authData.token)

                    // 🌟 Save the clear username using your updated helper
                    SharedPrefsHelper.saveString(this@SigninActivity, "auth_username", authData.username)

                    val configs = authData.configs
                    VpnDataManager.currentConfigs = configs
                    SharedPrefsHelper.saveConfigs(this@SigninActivity, configs)

                    // 🌟 Updated to match camelCase parameters from data.kt
                    SharedPrefsHelper.saveLong(this@SigninActivity, "data_limit", authData.dataLimit)
                    SharedPrefsHelper.saveLong(this@SigninActivity, "remaining_bytes", authData.remainingBytes)

                    startActivity(Intent(this@SigninActivity, MainActivity::class.java))
                    finish()
                } else {
                    Toast.makeText(this@SigninActivity, "خطا در ورود", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Log.e("Login", "Error: ${e.message}")
                Toast.makeText(this@SigninActivity, "خطا در اتصال به سرور", Toast.LENGTH_SHORT).show()
            } finally {
                loading.visibility = View.GONE
                btnSubmit.isEnabled = true
            }
        }
    }
}