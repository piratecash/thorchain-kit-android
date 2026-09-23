package io.horizontalsystems.thorchainkit.network

import io.horizontalsystems.thorchainkit.PlatformContext

public expect class ConnectionManager(context: PlatformContext) {

    public interface Listener {
        public fun onConnectionChange()
    }

    public var listener: Listener?

    public val isConnected: Boolean

    public fun start()

    public fun stop()
}
