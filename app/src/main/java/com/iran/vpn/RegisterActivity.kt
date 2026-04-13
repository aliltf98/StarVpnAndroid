package com.iran.vpn

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
import kotlin.math.log

class RegisterActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_signup) // وصل شدن به همان XML مرحله ۱
        val btnGoToSignin = findViewById<Button>(R.id.btnSwitch)

        val intent = Intent(this, SigninActivity::class.java)
        btnGoToSignin.setOnClickListener {
            startActivity(intent)
            finish() // این خط باعث می‌شود صفحه ثبت نام بسته شود و به صفحه قبل برگردد
        }
        // پیدا کردن دکمه و فیلدها از روی XML
        val btnSubmit = findViewById<Button>(R.id.btnSubmit)
        val etUser = findViewById<EditText>(R.id.Username)
        val etPass = findViewById<EditText>(R.id.Password)
        val etPhone = findViewById<EditText>(R.id.Phone)
        val loading = findViewById<ProgressBar>(R.id.loadingCircle)

        // تعریف اتفاقی که بعد از کلیک می‌افتد
        btnSubmit.setOnClickListener {
            val userText = etUser.text.toString()
            val passText = etPass.text.toString()
            val phoneText = etPhone.text.toString()

            // ۱. نمایش لودینگ و غیرفعال کردن دکمه
            loading.visibility = View.VISIBLE
            btnSubmit.isEnabled = false

            // ۲. ارسال اطلاعات به جنگو (این بخش اصلی است)
            // ما از یک Coroutine استفاده می‌کنیم چون نباید اینترنت باعث هنگ کردن برنامه شود
            lifecycleScope.launch {
                try {
                    val requestData = SignupRequest(userText, passText, phoneText)
                    val result = RetrofitClient.instance.registerUser(requestData)

                    if (result.isSuccessful) {
                        // اگر جنگو کد ۲۰۰ یا ۲۰۱ برگرداند
                        startActivity(intent)
                        finish()
                        Toast.makeText(this@RegisterActivity, "ثبت نام موفق بود!", Toast.LENGTH_SHORT).show()
                    } else {
                        // اگر جنگو کد ۴۰۰ برگرداند (مثلاً یوزر تکراری)
                        Toast.makeText(this@RegisterActivity, "خطا از سمت سرور", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    // اگر کلاً اینترنت قطع بود یا سرور روشن نبود
                    Toast.makeText(this@RegisterActivity, "خطای شبکه: ${e.message}", Toast.LENGTH_SHORT).show()
                } finally {
                    // در هر صورت لودینگ را مخفی کن
                    loading.visibility = View.GONE
                    btnSubmit.isEnabled = true
                }
            }
        }
    }
}