package io.horizontalsystems.thorchainkit.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.NetworkRequest

// Registers on start() and unregisters on stop(), and can be restarted: a
// stop()/start() cycle gets a fresh callback registration, so connectivity
// changes keep being observed across kit restarts.
actual class ConnectionManager actual constructor(context: Context) {

    actual interface Listener {
        actual fun onConnectionChange()
    }

    private val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    actual var listener: Listener? = null

    @Volatile
    private var connected = false

    actual val isConnected: Boolean
        get() = connected

    private val lock = Any()
    private var hasValidInternet = false
    private var hasConnection = false
    private var callback: ConnectionStatusCallback? = null

    // register/unregister happen INSIDE the lock: a start/stop race must never leave
    // a callback registered with the system while `callback` is already null (such a
    // callback would leak until the process dies)
    actual fun start() {
        synchronized(lock) {
            if (callback != null) return
            connected = getInitialConnectionStatus()
            val cb = ConnectionStatusCallback()
            callback = cb
            connectivityManager.registerNetworkCallback(NetworkRequest.Builder().build(), cb)
        }
    }

    actual fun stop() {
        synchronized(lock) {
            val current = callback ?: return
            callback = null
            try {
                connectivityManager.unregisterNetworkCallback(current)
            } catch (e: Exception) {
                // already unregistered
            }
        }
    }

    private fun getInitialConnectionStatus(): Boolean {
        val network = connectivityManager.activeNetwork ?: run {
            hasConnection = false
            hasValidInternet = false
            return false
        }

        hasConnection = true
        val capabilities = connectivityManager.getNetworkCapabilities(network)
        hasValidInternet = capabilities?.let {
            it.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) && it.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        } ?: false

        return hasValidInternet
    }

    inner class ConnectionStatusCallback : ConnectivityManager.NetworkCallback() {

        private val activeNetworks: MutableList<android.net.Network> = mutableListOf()

        override fun onLost(network: android.net.Network) {
            super.onLost(network)
            synchronized(lock) {
                if (callback !== this) return
                activeNetworks.removeAll { activeNetwork -> activeNetwork == network }
                hasConnection = activeNetworks.isNotEmpty()
            }
            updatedConnectionState()
        }

        override fun onCapabilitiesChanged(network: android.net.Network, networkCapabilities: NetworkCapabilities) {
            super.onCapabilitiesChanged(network, networkCapabilities)
            synchronized(lock) {
                if (callback !== this) return
                hasValidInternet = networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                        && networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
            }
            updatedConnectionState()
        }

        override fun onAvailable(network: android.net.Network) {
            super.onAvailable(network)
            synchronized(lock) {
                if (callback !== this) return
                if (activeNetworks.none { activeNetwork -> activeNetwork == network }) {
                    activeNetworks.add(network)
                }
                hasConnection = activeNetworks.isNotEmpty()
            }
            updatedConnectionState()
        }
    }

    private fun updatedConnectionState() {
        val changed = synchronized(lock) {
            val oldValue = connected
            connected = hasConnection && hasValidInternet
            oldValue != connected
        }
        if (changed) {
            listener?.onConnectionChange()
        }
    }
}
