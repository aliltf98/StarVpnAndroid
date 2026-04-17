package com.iran.vpn

data class SignupRequest(
    val username: String,
    val password: String,
    val number: String,
)

data class SigninRequest(
    val username: String,
    val password: String,
)

data class AuthResponse(
    val status: String,
    val token: String?, // توکن دریافتی از جنگو
    val message: String?
)