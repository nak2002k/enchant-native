package org.enchant.core.network

import okhttp3.OkHttpClient
import java.net.InetSocketAddress
import java.net.Proxy

object DomainFronting {
    private var proxyHost: String? = null
    private var proxyPort: Int = 0
    private var isEnabled: Boolean = false

    fun configure(host: String?, port: Int, enabled: Boolean) {
        proxyHost = host
        proxyPort = port
        isEnabled = enabled && host != null && port > 0
    }

    fun applyToClient(builder: OkHttpClient.Builder): OkHttpClient.Builder {
        if (!isEnabled || proxyHost == null) return builder
        return builder.proxy(Proxy(Proxy.Type.HTTP, InetSocketAddress(proxyHost!!, proxyPort)))
    }

    fun isConfigured(): Boolean = isEnabled && proxyHost != null
}
