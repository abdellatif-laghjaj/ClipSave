package com.abdellatif.clipsave.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Build
import com.abdellatif.clipsave.data.preferences.NetworkPolicy
import java.io.Closeable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class NetworkState(
    val connected: Boolean,
    val unmetered: Boolean
) {
    fun isEligible(policy: NetworkPolicy): Boolean =
        connected && (policy == NetworkPolicy.ANY || unmetered)
}

/**
 * Process-local view of the default network. A single callback is shared by every queued transfer
 * so waiting downloads do not poll, wake the radio, or leak per-item connectivity callbacks.
 */
class NetworkMonitor(context: Context) : Closeable {

    private val connectivity =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    private val _state = MutableStateFlow(readCurrentState())
    val state: StateFlow<NetworkState> = _state.asStateFlow()

    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) = refresh()
        override fun onLost(network: Network) = refresh()

        override fun onCapabilitiesChanged(
            network: Network,
            networkCapabilities: NetworkCapabilities
        ) = refresh()
    }

    init {
        connectivity.registerDefaultNetworkCallback(callback)
        refresh()
    }

    private fun refresh() {
        _state.value = readCurrentState()
    }

    private fun readCurrentState(): NetworkState {
        val network = connectivity.activeNetwork ?: return NetworkState(false, false)
        val capabilities = connectivity.getNetworkCapabilities(network)
            ?: return NetworkState(false, false)
        val connected = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        val unmetered = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED) ||
            (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
                capabilities.hasCapability(
                    NetworkCapabilities.NET_CAPABILITY_TEMPORARILY_NOT_METERED
                ))
        return NetworkState(connected, unmetered)
    }

    override fun close() {
        runCatching { connectivity.unregisterNetworkCallback(callback) }
    }
}
