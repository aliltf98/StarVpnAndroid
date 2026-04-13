package com.iran.vpn

import android.content.Context
import android.util.Log
import java.io.File
import java.io.FileOutputStream



object EngineManager {
    private var tunProcess: Process? = null

    fun startTun2Socks(context: Context, vpnInterfaceFd: Int) {
        try {
            val tunBinary = File(context.filesDir, "tun2socks")

            // دستور اجرا: tun2socks باید به اینترفیس VPN متصل شود
            // --tunFd: آدرس فایل توصیف‌گر شبکه اندروید
            // --proxy: آدرس پورت Socks5 که Xray باز کرده است
            val command = "${tunBinary.absolutePath} --tunFd $vpnInterfaceFd --proxy socks5://127.0.0.1:10808"

            tunProcess = Runtime.getRuntime().exec(command)
            Log.d("EngineManager", "موتور Tun2Socks با موفقیت متصل شد.")
        } catch (e: Exception) {
            Log.e("EngineManager", "خطا در اجرای Tun2Socks: ${e.message}")
        }
    }
    private var xrayProcess: Process? = null

    fun startEngine(context: Context, config: ProxyConfig) {
        try {
            val binary = File(context.filesDir, "xray")
            val configFile = File(context.filesDir, "config.json")

            // ۱. ذخیره تنظیمات در یک فایل موقت
            configFile.writeText(config.generateXrayJson())

            // ۲. اجرای دستور در لینوکسِ اندروید
            // دستور: xray run -c config.json
            val command = "${binary.absolutePath} run -c ${configFile.absolutePath}"

            xrayProcess = Runtime.getRuntime().exec(command)

            Log.d("EngineManager", "هسته با موفقیت روشن شد.")

        } catch (e: Exception) {
            Log.e("EngineManager", "خطا در روشن کردن هسته: ${e.message}")
        }
    }

    fun stopEngine() {
        xrayProcess?.destroy()
        xrayProcess = null
        Log.d("EngineManager", "هسته خاموش شد.")
    }

    private const val BINARY_NAME = "xray" // نامِ پرونده‌یِ هسته
    private const val TAG = "EngineManager"

    // تابعی برای کپی کردنِ پرونده از assets به نشستگاهِ درونیِ گوشی
    fun extractEngine(context: Context): File? {
        // ساختنِ نشانیِ پرونده در حافظه‌یِ نهانِ برنامه
        val exeFile = File(context.filesDir, BINARY_NAME)

        // اگر پرونده از پیش هست و پروانه‌یِ راه‌اندازی دارد، نیازی به کپیِ دوباره نیست
        if (exeFile.exists() && exeFile.canExecute()) {
            Log.d(TAG, "هسته از پیش آماده است.")
            return exeFile
        }

        return try {
            // خواندنِ پرونده از دارایی‌ها (assets)
            val inputStream = context.assets.open(BINARY_NAME)
            val outputStream = FileOutputStream(exeFile)

            // کپی کردنِ بایت به بایت
            inputStream.copyTo(outputStream)

            inputStream.close()
            outputStream.close()

            // دادنِ پروانه‌یِ راه‌اندازی به پرونده (همانند دستور chmod 755 در لینوکس)
            exeFile.setExecutable(true)

            Log.d(TAG, "هسته با کامیابی کپی و آماده‌یِ کار شد.")
            exeFile // بازگرداندنِ پرونده
        } catch (e: Exception) {
            Log.e(TAG, "لغزش در کپی کردنِ هسته: ${e.message}")
            null
        }
    }
    fun stopAll() {
        try {
            // ابتدا موتور واسط را می‌بندیم
            tunProcess?.destroy()
            tunProcess = null

            // سپس هسته اصلی را خاموش می‌کنیم
            xrayProcess?.destroy()
            xrayProcess = null

            Log.d("EngineManager", "تمامی موتورها با موفقیت خاموش شدند.")
        } catch (e: Exception) {
            Log.e("EngineManager", "خطا در بستن فرآیندها: ${e.message}")
        }
    }
}