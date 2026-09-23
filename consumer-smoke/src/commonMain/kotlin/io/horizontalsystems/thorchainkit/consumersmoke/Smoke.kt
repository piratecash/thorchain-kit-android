package io.horizontalsystems.thorchainkit.consumersmoke

import io.horizontalsystems.thorchainkit.DatabaseEncryptionException
import io.horizontalsystems.thorchainkit.DatabaseKeyMismatchException
import io.horizontalsystems.thorchainkit.ThorchainKit
import io.horizontalsystems.thorchainkit.models.Address
import io.horizontalsystems.thorchainkit.network.Network
import io.horizontalsystems.thorchainkit.transaction.Signer
import io.horizontalsystems.thorchainkit.transaction.TxBuilder
import java.math.BigInteger

// Compile-only smoke: never executed. Proves the published artifact resolves and its
// transitive proto classes (com.google.protobuf.Any via TxBuilder.msgSend) are on the
// consumer's compile classpath for both JVM targets.
internal fun commonSmoke(seed: ByteArray) {
    val network = Network.Mainnet
    val address = ThorchainKit.getAddress(seed, network)
    val toAddress = Address.fromString(address.toString(), network)
    Signer.getInstance(seed, network)

    TxBuilder.msgSend(address, toAddress, BigInteger.ONE, network.nativeDenom)
}

// Proves the encryption exception types are exported with their subtype relation intact.
internal fun isKeyMismatch(failure: Throwable): Boolean {
    val encryptionFailure: DatabaseEncryptionException? = failure as? DatabaseKeyMismatchException
    return encryptionFailure != null
}
