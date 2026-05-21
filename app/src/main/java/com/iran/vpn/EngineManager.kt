package com.iran.vpn

import android.content.Context
import android.util.Log
import go.Seq
import libv2ray.CoreCallbackHandler
import libv2ray.CoreController
import libv2ray.Libv2ray

object EngineManager {

    private const val TAG = "EngineManager"
    private var coreController: CoreController? = null

    // Accept tunFd as an Integer so we can pass it to startLoop
    fun startEngine(context: Context, config: VlessConfig, tunFd: Int) {
        try {
            // ۱. مقداردهی اولیه به بستر Go Mobile
            Seq.setContext(context)

            // ۲. مقداردهی اولیه به محیط هسته
            val assetPath = context.filesDir.absolutePath
            Libv2ray.initCoreEnv(assetPath, "")

            // ۳. ایجاد هندلر برای دریافت وضعیت هسته Xray
            val callbackHandler = object : CoreCallbackHandler {
                override fun startup(): Long {
                    Log.d(TAG, "Xray Core در حال استارت زدن است...")
                    return 0
                }

                override fun shutdown(): Long {
                    Log.d(TAG, "Xray Core متوقف شد.")
                    return 0
                }

                override fun onEmitStatus(status: Long, message: String?): Long {
                    Log.d(TAG, "Xray Status [$status]: $message")
                    return 0
                }
            }

            // ۴. ساختن آبجکت کنترلر اصلی هسته
            val controller = Libv2ray.newCoreController(callbackHandler)

            // ۵. استارت زدن حلقه اصلی با تزریق کانفیگ و فایلاسکریپتور (FIXES ERRORS 1 & 2)
            // این متد همزمان کانفیگ را مقداردهی کرده و هسته را روشن میکند
            val jsonConfig = config.generateXrayJson(tunFd)
            controller.startLoop(jsonConfig, tunFd)

            coreController = controller
            Log.d(TAG, "هسته Xray با ساختار CoreController با موفقیت اجرا شد.")

        } catch (e: Exception) {
            Log.e(TAG, "خطا در استارت هسته Xray: ${e.message}")
        }
    }

    fun stopAll() {
        try {
            if (coreController != null) {
                // ۶. متوقف کردن امن حلقه فرآیند هسته (FIXES ERROR 3)
                coreController?.stopLoop()
                coreController = null
                Log.d(TAG, "هسته Xray متوقف شد.")
            }
        } catch (e: Exception) {
            Log.e(TAG, "خطا در متوقف کردن هسته: ${e.message}")
        }
    }
}