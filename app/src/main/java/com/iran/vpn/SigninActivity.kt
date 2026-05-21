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
        setContentView(R.layout.activity_signin) // وصل شدن به همان XML مرحله ۱
//        val btnGoToSignup = findViewById<Button>(R.id.btnSwitch) // همان دکمه شفاف پایین

//        btnGoToSignup.setOnClickListener {
//            val intent = Intent(this, RegisterActivity::class.java)
//            startActivity(intent)
//            finish()
//        }
        val manufacturer = android.os.Build.MANUFACTURER // مثال: Samsung
        val model = android.os.Build.MODEL               // مثال: SM-G991B (همان S21)
        val deviceName = "$manufacturer $model"         // خروجی: Samsung SM-G991B

        val btnSubmit = findViewById<Button>(R.id.btnSubmit)
        val loading = findViewById<ProgressBar>(R.id.loadingCircle)
        btnSubmit.setOnClickListener {

            // ۱. نمایش لودینگ و غیرفعال کردن دکمه
            loading.visibility = View.VISIBLE
            btnSubmit.isEnabled = false

            performLogin()

            // ۲. ارسال اطلاعات به جنگو (این بخش اصلی است)
            // ما از یک Coroutine استفاده می‌کنیم چون نباید اینترنت باعث هنگ کردن برنامه شود

        }

    }
    @SuppressLint("HardwareIds")
    private fun performLogin() {
        val btnSubmit = findViewById<Button>(R.id.btnSubmit)
        val loading = findViewById<ProgressBar>(R.id.loadingCircle)
        val username = findViewById<EditText>(R.id.Username).text.toString()
        val password = findViewById<EditText>(R.id.Password).text.toString()

        // ۱. استخراج اطلاعات گوشی
        val androidId = android.provider.Settings.Secure.getString(contentResolver,android.provider.Settings.Secure.ANDROID_ID)
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
                if (response.isSuccessful) {
                    val authData = response.body()

                    // ۲. ذخیره توکن در حافظه موقت یا SharedPrefs
                    SharedPrefsHelper.saveToken(this@SigninActivity, authData?.token)

                    // ۳. مدیریت کانفیگ‌ها (بدون نیاز به دیتابیس)
                    authData?.configs?.let { configs ->
                        // این لیست را به MainActivity بفرستید یا در یک Object سراسری ذخیره کنید
                        VpnDataManager.currentConfigs = configs
                        SharedPrefsHelper.saveConfigs(this@SigninActivity, configs) // ذخیره دائمی
                    }

                    startActivity(Intent(this@SigninActivity, MainActivity::class.java))
                    finish()
                } else {
                    Toast.makeText(this@SigninActivity, "خطا در ورود", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Log.e("Login", "Error: ${e.message}")
            } finally {
                loading.visibility = View.GONE
                btnSubmit.isEnabled = true
            }
        }
    }
}