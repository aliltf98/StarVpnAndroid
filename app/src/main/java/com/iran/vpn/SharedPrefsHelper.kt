package com.iran.vpn

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

object SharedPrefsHelper {
    private const val PREF_NAME = "user_prefs"
    private const val KEY_TOKEN = "auth_token"

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    }

    // ذخیره توکن
    fun saveToken(context: Context, token: String?) {
        getPrefs(context).edit().putString(KEY_TOKEN, token).apply()
    }

    // بازخوانی توکن
    fun getToken(context: Context): String? {
        return getPrefs(context).getString(KEY_TOKEN, null)
    }

    // پاک کردن توکن (هنگام خروج)
    fun clearPrefs(context: Context) {
        getPrefs(context).edit().clear().apply()
    }
    private const val KEY_CONFIGS = "saved_configs"

    // ذخیره لیست کانفیگ‌ها
    fun saveConfigs(context: Context, configs: List<VpnConfigResponse>?) {
        val json = Gson().toJson(configs)
        getPrefs(context).edit().putString(KEY_CONFIGS, json).apply()
    }

    // بازیابی لیست کانفیگ‌ها
    fun getConfigs(context: Context): List<VpnConfigResponse>? {
        val json = getPrefs(context).getString(KEY_CONFIGS, null) ?: return null
        val type = object : TypeToken<List<VpnConfigResponse>>() {}.type
        return Gson().fromJson(json, type)
    }
    // 🌟 Added to support long values for account bandwidth metrics
    fun saveLong(context: Context, key: String, value: Long) {
        getPrefs(context).edit().putLong(key, value).apply()
    }

    fun getLong(context: Context, key: String, defaultValue: Long = 0L): Long {
        return getPrefs(context).getLong(key, defaultValue)
    }
    // 🌟 Add these to SharedPrefsHelper.kt
    fun saveString(context: Context, key: String, value: String) {
        getPrefs(context).edit().putString(key, value).apply()
    }

    fun getString(context: Context, key: String, defaultValue: String = ""): String {
        return getPrefs(context).getString(key, defaultValue) ?: defaultValue
    }
}