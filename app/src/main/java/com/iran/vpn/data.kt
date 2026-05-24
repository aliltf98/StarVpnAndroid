package com.iran.vpn
import com.google.gson.annotations.SerializedName

data class SignupRequest(
    val username: String,
    val password: String,
    val number: String,
)

data class SigninRequest(
    val username: String,
    val password: String,
    val device_id: String,
    val model_name: String
)

// مدل برای هر کانفیگ که از جنگو می‌آید
data class VpnConfigResponse(
    val id: Int,
    val value: String, // همان لینک Vless
    val category_name: String,
    val remainingVolume: Double
)

// اصلاح پاسخ لاگین برای دریافت لیست کانفیگ‌ها
data class AuthResponse(
    val token: String,
    val username: String,
    val message: String,
    val configs: List<VpnConfigResponse>,

    @SerializedName("data_limit")
    val dataLimit: Long,

    @SerializedName("remaining_bytes")
    val remainingBytes: Long
)

