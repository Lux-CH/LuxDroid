package ch.cclerc.luxapp.core

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object NetworkMonitor {
    private var connectivityManager: ConnectivityManager? = null
    private var callback: ConnectivityManager.NetworkCallback? = null
    private val satisfied = mutableSetOf<Network>()

    private val _isOnline = MutableStateFlow(false)
    val isOnline: StateFlow<Boolean> = _isOnline.asStateFlow()

    fun init(context: Context) {
        connectivityManager = context.applicationContext
            .getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
    }

    fun start() {
        if (callback != null) return
        val manager = connectivityManager ?: return

        _isOnline.value = currentlySatisfied(manager)

        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()

        val newCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                synchronized(satisfied) {
                    satisfied.add(network)
                    _isOnline.value = satisfied.isNotEmpty()
                }
            }

            override fun onLost(network: Network) {
                synchronized(satisfied) {
                    satisfied.remove(network)
                    _isOnline.value = satisfied.isNotEmpty()
                }
            }

            override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                val usable = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                    capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
                synchronized(satisfied) {
                    if (usable) satisfied.add(network) else satisfied.remove(network)
                    _isOnline.value = satisfied.isNotEmpty()
                }
            }
        }

        runCatching { manager.registerNetworkCallback(request, newCallback) }
            .onSuccess { callback = newCallback }
    }

    fun stop() {
        val manager = connectivityManager
        val active = callback
        if (manager != null && active != null) {
            runCatching { manager.unregisterNetworkCallback(active) }
        }
        callback = null
        synchronized(satisfied) { satisfied.clear() }
    }

    private fun currentlySatisfied(manager: ConnectivityManager): Boolean {
        val network = manager.activeNetwork ?: return false
        val capabilities = manager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }
}
