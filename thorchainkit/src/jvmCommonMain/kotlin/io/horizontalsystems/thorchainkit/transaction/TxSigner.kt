package io.horizontalsystems.thorchainkit.transaction

import io.horizontalsystems.thorchainkit.models.AccountInfo
import io.horizontalsystems.thorchainkit.network.Network

/** How a transaction gets signed: the seed key in SIGN_MODE_DIRECT, an external signer in legacy amino JSON. */
internal sealed class TxSigner {
    abstract val publicKey: ByteArray

    // The chain-id is pinned per network, never taken from the node, so a malicious endpoint cannot make the kit
    // sign a transaction valid on a different network.
    abstract suspend fun buildSigned(
        messages: List<TxMessage>,
        memo: String,
        account: AccountInfo,
        network: Network,
        gasLimit: Long
    ): ByteArray

    class Direct(private val signer: Signer) : TxSigner() {
        override val publicKey: ByteArray get() = signer.publicKey

        override suspend fun buildSigned(
            messages: List<TxMessage>,
            memo: String,
            account: AccountInfo,
            network: Network,
            gasLimit: Long
        ): ByteArray = TxBuilder.buildSigned(
            messages = messages.map { it.proto() },
            memo = memo,
            accountNumber = account.accountNumber,
            sequence = account.sequence,
            chainId = network.chainId,
            gasLimit = gasLimit,
            feeDenom = network.assetResolver.nativeDenom,
            signer = signer
        )
    }

    class Amino(private val signer: ThorchainExternalSigner) : TxSigner() {
        override val publicKey: ByteArray get() = signer.publicKey

        override suspend fun buildSigned(
            messages: List<TxMessage>,
            memo: String,
            account: AccountInfo,
            network: Network,
            gasLimit: Long
        ): ByteArray = TxBuilder.buildAminoSigned(
            messages = messages,
            memo = memo,
            accountNumber = account.accountNumber,
            sequence = account.sequence,
            chainId = network.chainId,
            gasLimit = gasLimit,
            feeAmount = null,
            feeDenom = network.assetResolver.nativeDenom,
            signer = signer
        )
    }
}
