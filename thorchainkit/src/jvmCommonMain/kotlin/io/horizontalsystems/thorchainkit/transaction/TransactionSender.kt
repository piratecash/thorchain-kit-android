package io.horizontalsystems.thorchainkit.transaction

import io.horizontalsystems.thorchainkit.models.Address
import io.horizontalsystems.thorchainkit.models.Asset
import io.horizontalsystems.thorchainkit.network.BroadcastAmbiguousError
import io.horizontalsystems.thorchainkit.network.BroadcastError
import io.horizontalsystems.thorchainkit.network.Network
import io.horizontalsystems.thorchainkit.network.ThornodeApiProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.math.BigInteger

class TransactionSender internal constructor(
    private val address: Address,
    private val network: Network,
    private val thornodeApiProvider: ThornodeApiProvider,
    private val confirmationAttempts: Int,
    private val confirmationDelayMs: Long
) {

    constructor(
        address: Address,
        network: Network,
        thornodeApiProvider: ThornodeApiProvider
    ) : this(address, network, thornodeApiProvider, CONFIRMATION_ATTEMPTS, CONFIRMATION_DELAY_MS)

    // serializes sends: concurrent sends would fetch the same sequence and race each other
    private val sendMutex = Mutex()

    suspend fun send(to: Address, amount: BigInteger, denom: String, memo: String?, signer: Signer): String {
        val message = TxBuilder.msgSend(address, to, amount, denom)
        return signAndBroadcast(listOf(message), memo ?: "", TxBuilder.DEFAULT_GAS_LIMIT, signer)
    }

    suspend fun deposit(asset: Asset, amount: BigInteger, memo: String, signer: Signer): String {
        val message = TxBuilder.msgDeposit(asset, amount, memo, address)
        return signAndBroadcast(listOf(message), "", TxBuilder.DEPOSIT_GAS_LIMIT, signer)
    }

    private suspend fun signAndBroadcast(
        messages: List<com.google.protobuf.Any>,
        memo: String,
        gasLimit: Long,
        signer: Signer
    ): String = sendMutex.withLock {
        val signerAddress = Address(address.prefix, io.horizontalsystems.hdwalletkit.Utils.sha256Hash160(signer.publicKey))
        if (signerAddress != address) {
            throw SendError.SignerMismatch()
        }

        val account = thornodeApiProvider.fetchAccount(address)
            ?: throw SendError.AccountNotFound()

        val txRaw = TxBuilder.buildSigned(
            messages = messages,
            memo = memo,
            accountNumber = account.accountNumber,
            sequence = account.sequence,
            // pinned per network — never taken from the node, so a malicious endpoint
            // cannot make the kit sign a transaction valid on a different network
            chainId = network.chainId,
            gasLimit = gasLimit,
            feeDenom = network.assetResolver.nativeDenom,
            signer = signer
        )

        try {
            thornodeApiProvider.broadcast(txRaw)
        } catch (error: BroadcastAmbiguousError) {
            resolveAmbiguousBroadcast(error)
        }
    }

    // The broadcast request failed locally, but the tx may have reached the node.
    // Poll for it before reporting failure — a plain failure invites a wallet-level
    // retry, and since the first tx may confirm, the retry would be a second payment.
    private suspend fun resolveAmbiguousBroadcast(ambiguity: BroadcastAmbiguousError): String {
        repeat(confirmationAttempts) {
            delay(confirmationDelayMs)

            val tx = try {
                thornodeApiProvider.fetchTransaction(ambiguity.txHash)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                null
            }

            val code = tx?.code
            if (tx != null && code != null) {
                if (code == 0) return ambiguity.txHash

                // included in a block but failed on-chain: definitive failure
                throw BroadcastError(code, tx.rawLog ?: "")
            }
            // tx == null (not yet included) or malformed response (code missing):
            // not resolved — never default a missing code to success
        }

        throw SendError.PossiblyAccepted(ambiguity.txHash, ambiguity)
    }

    sealed class SendError(cause: Throwable? = null) : Throwable(cause) {
        // the account has never received funds, so it does not exist on-chain yet
        class AccountNotFound : SendError()

        // the signer's key does not derive the kit's address (e.g. watch-account kit)
        class SignerMismatch : SendError()

        // The broadcast outcome is unknown: the transaction MAY have been accepted and
        // can still confirm. Do NOT blindly retry the payment — track txHash (via
        // fetchTransaction / history sync) until its status is known.
        class PossiblyAccepted(val txHash: String, cause: Throwable? = null) : SendError(cause) {
            override val message: String
                get() = "Broadcast result unknown; transaction $txHash may still confirm"
        }
    }

    companion object {
        // ~6s block time: 5 x 3s covers inclusion of an accepted tx
        private const val CONFIRMATION_ATTEMPTS = 5
        private const val CONFIRMATION_DELAY_MS = 3_000L
    }
}
