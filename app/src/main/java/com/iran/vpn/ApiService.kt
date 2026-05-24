package com.iran.vpn

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.GET
import retrofit2.http.Path

// 🌟 Model matching the Django UserVolumeStatus response
data class VolumeStatusResponse(
    val status: String,
    val username: String,
    val data_limit: Long,
    val used_traffic: Long,
    val remaining_bytes: Long
)

interface ApiService {
    // آدرسی که در urls.py جنگو تعریف کردید
    @POST("api/v1/signup/")
    suspend fun registerUser(@Body request: SignupRequest): Response<AuthResponse>

    @POST("api/v1/signin/")
    suspend fun loginUser(@Body request: SigninRequest): Response<AuthResponse>
    // 🌟 New endpoint matching your Django url structure
    @GET("api/v1/vpn-usage/{username}/")
    suspend fun getVolumeStatus(@Path("username") username: String): Response<VolumeStatusResponse>
}
