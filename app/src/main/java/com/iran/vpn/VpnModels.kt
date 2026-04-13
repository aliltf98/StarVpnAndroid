package com.iran.vpn

// یک Interface برای تمام پروتکل‌ها
interface ProxyConfig {
    fun generateXrayJson(): String
}

// مدل اختصاصی برای VLESS (پروتکل اصلی شما)
data class VlessConfig(
    val remark: String,
    val address: String,
    val port: Int,
    val uuid: String,
    val sni: String,
    val path: String = "/"
) : ProxyConfig {
//    override fun generateXrayJson(): String {
//        // اینجا ساختار JSON مخصوص Xray را می‌سازیم
//        // برای شروع، یک نسخه ساده شده برمی‌گردانیم
//        return """
//        {
//          "outbounds": [{
//            "protocol": "vless",
//            "settings": {
//              "vnext": [{
//                "address": "$address",
//                "port": $port,
//                "users": [{ "id": "$uuid", "encryption": "none" }]
//              }]
//            },
//            "streamSettings": {
//              "network": "ws",
//              "security": "tls",
//              "tlsSettings": { "serverName": "$sni" }
//            }
//          }]
//        }
//        """.trimIndent()
//    }
    override fun generateXrayJson(): String {
        return """
    {
      "inbounds": [{
        "port": 10808,
        "protocol": "socks",
        "settings": { "auth": "noauth", "udp": true }
      }],
      "outbounds": [{
        "protocol": "vless",
        "settings": {
          "vnext": [{
            "address": "$address",
            "port": $port,
            "users": [{ "id": "$uuid", "encryption": "none" }]
          }]
        },
        "streamSettings": {
          "network": "ws", 
          "security": "tls",
          "tlsSettings": { "serverName": "$sni" }
        }
      }]
    }
    """.trimIndent()
    }
}