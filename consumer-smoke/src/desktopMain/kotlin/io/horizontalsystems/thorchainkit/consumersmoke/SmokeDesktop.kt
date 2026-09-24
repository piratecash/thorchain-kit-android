package io.horizontalsystems.thorchainkit.consumersmoke

import io.horizontalsystems.thorchainkit.ThorchainKit
import io.horizontalsystems.thorchainkit.clear
import io.horizontalsystems.thorchainkit.getInstance
import io.horizontalsystems.thorchainkit.models.Address
import io.horizontalsystems.thorchainkit.network.Network
import okhttp3.EventListener
import java.io.File

// Compile-only smoke: proves the desktop `File`-based factory/clear extensions resolve
// from the published artifact's desktop variant.
internal fun desktopSmoke(dataDir: File, address: Address, databaseKey: ByteArray) {
    ThorchainKit.getInstance(dataDir, address, Network.Mainnet, "smoke", databaseKey)
    ThorchainKit.clear(dataDir, Network.Mainnet, "smoke")
}

// Compile-only smoke: proves okhttp3.EventListener resolves on the consumer's compile
// classpath through the published artifact's transitive dependencies alone.
internal fun desktopSmokeWithEventListener(dataDir: File, address: Address, databaseKey: ByteArray) {
    ThorchainKit.getInstance(
        dataDir, address, Network.Mainnet, "smoke", databaseKey,
        15, Network.Mainnet.thornodeUrls, Network.Mainnet.midgardUrls,
        EventListener.Factory { EventListener.NONE }
    )
}
