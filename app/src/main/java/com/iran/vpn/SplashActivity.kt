package com.iran.vpn

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity

class SplashActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        // ایجاد یک وقفه کوتاه (مثلاً ۲ ثانیه) برای نمایش لوگو
        Handler(Looper.getMainLooper()).postDelayed({
            checkLoginStatus()
        }, 2000)
    }

    private fun checkLoginStatus() {
        // چک کردن وجود توکن در SharedPreferences
        val token = SharedPrefsHelper.getToken(this)

        if (token != null) {
            // کاربر قبلاً وارد شده است -> هدایت به صفحه اصلی
            startActivity(Intent(this, MainActivity::class.java))
        } else {
            // کاربر لاگین نیست -> هدایت به صفحه ورود
            startActivity(Intent(this, SigninActivity::class.java))
        }

        // بستن SplashActivity تا کاربر نتواند به آن برگردد
        finish()
    }
}