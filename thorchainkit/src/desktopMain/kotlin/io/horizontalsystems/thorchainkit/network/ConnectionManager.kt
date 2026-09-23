package io.horizontalsystems.thorchainkit.network

import io.horizontalsystems.thorchainkit.PlatformContext

// Desktop has no connectivity service to subscribe to, so the kit always syncs;
// SyncTimer.start() evaluates isConnected itself, so the listener is never called.
public actual class ConnectionManager actual constructor(context: PlatformContext) {

    public actual interface Listener {
        public actual fun onConnectionChange()
    }

    public actual var listener: Listener? = null

    public actual val isConnected: Boolean = true

    public actual fun start(): Unit = Unit

    public actual fun stop(): Unit = Unit
}
