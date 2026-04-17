package com.iran.vpn

import android.content.Intent
import android.os.Bundle
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
        val btnGoToSignup = findViewById<Button>(R.id.btnSwitch) // همان دکمه شفاف پایین

        btnGoToSignup.setOnClickListener {
            val intent = Intent(this, RegisterActivity::class.java)
            startActivity(intent)
            finish()
        }

        val btnSubmit = findViewById<Button>(R.id.btnSubmit)
        val etUser = findViewById<EditText>(R.id.Username)
        val etPass = findViewById<EditText>(R.id.Password)
        val loading = findViewById<ProgressBar>(R.id.loadingCircle)
        btnSubmit.setOnClickListener {
            val userText = etUser.text.toString()
            val passText = etPass.text.toString()

            // ۱. نمایش لودینگ و غیرفعال کردن دکمه
            loading.visibility = View.VISIBLE
            btnSubmit.isEnabled = false

            // ۲. ارسال اطلاعات به جنگو (این بخش اصلی است)
            // ما از یک Coroutine استفاده می‌کنیم چون نباید اینترنت باعث هنگ کردن برنامه شود
            lifecycleScope.launch {
                try {
                    val requestData = SigninRequest(userText, passText)
                    val result = RetrofitClient.instance.loginUser(requestData)

                    if (result.isSuccessful) {
                        // اصلاح اصلی: باید یک Intent جدید برای مقصد بسازید
                        val nextIntent = Intent(this@SigninActivity, MainActivity::class.java)
                        startActivity(nextIntent)

                        finish()
                        runOnUiThread {
                            Toast.makeText(this@SigninActivity, "ورود موفق بود!", Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        // نمایش پیام خطا از سمت سرور
                        Toast.makeText(this@SigninActivity, "نام کاربری یا رمز عبور اشتباه است", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    Toast.makeText(this@SigninActivity, "خطای شبکه: ${e.message}", Toast.LENGTH_SHORT).show()
                } finally {
                    loading.visibility = View.GONE
                    btnSubmit.isEnabled = true
                }
            }
        }

    }
}