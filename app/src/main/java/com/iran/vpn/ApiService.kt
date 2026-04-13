package com.iran.vpn

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

interface ApiService {
    // آدرسی که در urls.py جنگو تعریف کردید
    @POST("api/v1/signup/")
    suspend fun registerUser(@Body request: SignupRequest): Response<AuthResponse>

    @POST("api/v1/signin/")
    suspend fun loginUser(@Body request: SigninRequest): Response<AuthResponse>
}