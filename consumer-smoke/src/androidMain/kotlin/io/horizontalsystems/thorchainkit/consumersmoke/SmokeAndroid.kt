package io.horizontalsystems.thorchainkit.consumersmoke

import android.content.Context
import io.horizontalsystems.thorchainkit.ThorchainKit
import io.horizontalsystems.thorchainkit.models.Address
import io.horizontalsystems.thorchainkit.network.Network

// Compile-only smoke: proves the Android factory/clear overloads resolve from the
// published artifact's Android variant.
internal fun androidSmoke(context: Context, address: Address, databaseKey: ByteArray) {
    ThorchainKit.getInstance(context, address, Network.Mainnet, "smoke", databaseKey)
    ThorchainKit.clear(context, Network.Mainnet, "smoke")
}
