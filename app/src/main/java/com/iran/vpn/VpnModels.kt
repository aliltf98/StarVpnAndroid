package com.iran.vpn

import android.net.Uri

interface ProxyConfig {
    fun generateXrayJson(tunFd: Int): String
}

data class VlessConfig(
    val remark: String,
    val address: String,
    val port: Int,
    val uuid: String,
    val security: String,
    val network: String,
    val path: String,
    val host: String
) : ProxyConfig {

    override fun generateXrayJson(tunFd: Int): String {
        val tlsConfig = if (security == "tls") {
            """
        "tlsSettings": {
          "serverName": "$host",
          "allowInsecure": false
        },
        """.trimIndent()
        } else ""

        val transportSettings = if (network == "ws") {
            """
        "wsSettings": {
          "path": "$path",
          "host": "$host"
        }
        """.trimIndent()
        } else {
            """
        "tcpSettings": {}
        """.trimIndent()
        }

        return """
    {
      "log": { "loglevel": "warning" },
      "dns": {
        "servers": ["1.1.1.1", "8.8.8.8"],
        "queryStrategy": "UseIPv4"
      },
      "inbounds": [
        {
          "tag": "socks-in",
          "port": 10808,
          "listen": "127.0.0.1",
          "protocol": "socks",
          "settings": { "auth": "noauth", "udp": true }
        },
        {
          "tag": "tun-in",
          "port": 10809,
          "protocol": "tun",
          "settings": {
            "fd": $tunFd,
            "mtu": 1500,
            "autoRoute": false
          },
          "sniffing": {
            "enabled": true,
            "destOverride": ["http", "tls", "quic"]
          }
        }
      ],
      "outbounds": [
        {
          "tag": "proxy",
          "protocol": "vless",
          "settings": {
            "vnext": [{
              "address": "$address",
              "port": $port,
              "users": [{ "id": "$uuid", "encryption": "none" }]
            }]
          },
          "streamSettings": {
            "network": "$network",
            "security": "${if (security == "tls") "tls" else "none"}",
            $tlsConfig
            $transportSettings
          }
        },
        { "protocol": "freedom", "tag": "direct" },
        { "protocol": "blackhole", "tag": "block" },
        { "protocol": "dns", "tag": "dns-out" } // 🌟 Added dedicated internal DNS handler
      ],
      "routing": {
        "domainStrategy": "IPIfNonMatch", // Changed to let Xray resolve domains if they match IP rules
        "rules": [
          {
            "type": "field",
            "port": 53,
            "outboundTag": "dns-out" // 🌟 Forces all DNS queries into Xray's secure DNS system
          },
          {
            "type": "field",
            "outboundTag": "direct",
            "ip": ["127.0.0.1/8", "10.0.0.0/8", "172.16.0.0/12", "192.168.0.0/16"]
          },
          {
            "type": "field",
            "outboundTag": "proxy",
            "network": "udp,tcp"
          }
        ]
      }
    }
    """.trimIndent()
    }
}

fun parseVlessUri(vlessLink: String): VlessConfig? {
    return try {
        val uri = Uri.parse(vlessLink)
        val uuid = uri.userInfo
        val address = uri.host ?: ""
        val port = uri.port

        val security = uri.getQueryParameter("security") ?: "none"
        val network = uri.getQueryParameter("type") ?: "tcp"
        val host = uri.getQueryParameter("host")?.takeIf { it.isNotEmpty() } ?: address
        val rawPath = uri.getQueryParameter("path") ?: ""
        val path = if (rawPath.startsWith("/")) rawPath else "/$rawPath"
        val remark = uri.fragment ?: "Server"

        if (uuid != null && address.isNotEmpty()) {
            VlessConfig(remark, address, port, uuid, security, network, path, host)
        } else null
    } catch (e: Exception) {
        null
    }
}
