package io.horizontalsystems.thorchainkit.consumersmoke

import io.horizontalsystems.thorchainkit.ThorchainKit
import io.horizontalsystems.thorchainkit.clear
import io.horizontalsystems.thorchainkit.getInstance
import io.horizontalsystems.thorchainkit.models.Address
import io.horizontalsystems.thorchainkit.network.Network
import java.io.File

// Compile-only smoke: proves the desktop `File`-based factory/clear extensions resolve
// from the published artifact's desktop variant.
internal fun desktopSmoke(dataDir: File, address: Address, databaseKey: ByteArray) {
    ThorchainKit.getInstance(dataDir, address, Network.Mainnet, "smoke", databaseKey)
    ThorchainKit.clear(dataDir, Network.Mainnet, "smoke")
}
