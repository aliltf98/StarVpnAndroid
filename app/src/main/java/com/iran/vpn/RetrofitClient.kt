package com.iran.vpn

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object RetrofitClient {

//    private const val BASE_URL = "http://0.0.0.0:8000"
//    private const val BASE_URL = "http://192.168.1.145:8000"
    private const val BASE_URL = "http://192.168.137.1:8000"

    val instance: ApiService by lazy {
        val retrofit = Retrofit.Builder()
            .baseUrl(BASE_URL)
            // این خط باعث می‌شود خروجی‌های جنگو خودکار به کلاس‌های اندروید تبدیل شوند
            .addConverterFactory(GsonConverterFactory.create())

            .build()

            retrofit.create(ApiService::class.java)
    }
}