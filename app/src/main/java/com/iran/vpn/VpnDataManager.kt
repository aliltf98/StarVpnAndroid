package com.iran.vpn

object VpnDataManager {
    var currentConfigs: List<VpnConfigResponse> = mutableListOf()

    // Holds the config that the VpnService will use when it starts
    var pendingConfig: VlessConfig? = null

    fun getRemainingVolume(): String {
        return if (currentConfigs.isNotEmpty()) "${currentConfigs[0].remainingVolume} GB" else "0 GB"
    }

    fun getConfigsByType(protocol: String): List<VpnConfigResponse> {
        return currentConfigs.filter { it.value.startsWith(protocol, ignoreCase = true) }
    }

    fun clearData() {
        currentConfigs = mutableListOf()
        pendingConfig = null
    }
}