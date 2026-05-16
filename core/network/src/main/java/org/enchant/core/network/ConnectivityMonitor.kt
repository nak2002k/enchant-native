package org.enchant.core.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class NetworkType { WIFI, CELLULAR, ETHERNET, NONE }

object ConnectivityMonitor {
    private var initialized = false
    private val _isOnline = MutableStateFlow(true)
    private val _networkType = MutableStateFlow(NetworkType.NONE)
    private var callback: ConnectivityManager.NetworkCallback? = null

    val isOnline: StateFlow<Boolean> = _isOnline.asStateFlow()
    val networkType: StateFlow<NetworkType> = _networkType.asStateFlow()

    fun init(context: Context) {
        if (initialized) return
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return
        val activeNetwork = cm.activeNetwork
        val capabilities = cm.getNetworkCapabilities(activeNetwork)
        updateState(capabilities)

        callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                val caps = cm.getNetworkCapabilities(network)
                updateState(caps)
            }

            override fun onLost(network: Network) {
                val active = cm.activeNetwork
                val caps = cm.getNetworkCapabilities(active)
                updateState(caps)
            }

            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                updateState(caps)
            }
        }

        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        cm.registerNetworkCallback(request, callback!!)
        initialized = true
    }

    private fun updateState(capabilities: NetworkCapabilities?) {
        if (capabilities == null) {
            _isOnline.value = false
            _networkType.value = NetworkType.NONE
            return
        }
        _isOnline.value = true
        _networkType.value = when {
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> NetworkType.WIFI
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> NetworkType.CELLULAR
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> NetworkType.ETHERNET
            else -> NetworkType.NONE
        }
    }
}
