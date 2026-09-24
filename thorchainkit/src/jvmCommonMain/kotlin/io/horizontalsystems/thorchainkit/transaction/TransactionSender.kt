package io.horizontalsystems.thorchainkit.transaction

import com.google.protobuf.Any as ProtoAny
import io.horizontalsystems.hdwalletkit.Utils
import io.horizontalsystems.thorchainkit.models.Address
import io.horizontalsystems.thorchainkit.models.Asset
import io.horizontalsystems.thorchainkit.models.SignedTransaction
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

    suspend fun send(to: Address, amount: BigInteger, denom: String, memo: String?, signer: Signer): String =
        sendMutex.withLock {
            broadcastRawTransaction(signMsgSend(to, amount, denom, memo, signer).raw)
        }

    public suspend fun signSend(to: Address, amount: BigInteger, denom: String, memo: String?, signer: Signer): SignedTransaction =
        sendMutex.withLock { signMsgSend(to, amount, denom, memo, signer) }

    suspend fun deposit(asset: Asset, amount: BigInteger, memo: String, signer: Signer): String =
        sendMutex.withLock {
            val message = TxBuilder.msgDeposit(asset, amount, memo, address)
            broadcastRawTransaction(sign(listOf(message), "", TxBuilder.DEPOSIT_GAS_LIMIT, signer).raw)
        }

    public suspend fun broadcastRawTransaction(raw: ByteArray): String =
        try {
            thornodeApiProvider.broadcast(raw)
        } catch (error: BroadcastAmbiguousError) {
            resolveAmbiguousBroadcast(error)
        } catch (error: BroadcastError) {
            throw if (error.isSequenceConsumed()) SendError.SequenceConsumed(error) else error
        }

    private suspend fun signMsgSend(
        to: Address,
        amount: BigInteger,
        denom: String,
        memo: String?,
        signer: Signer
    ): SignedTransaction {
        require(to.prefix == network.addressPrefix) { "Address prefix mismatch: ${to.prefix}" }

        val message = TxBuilder.msgSend(address, to, amount, denom)
        return sign(listOf(message), memo ?: "", TxBuilder.DEFAULT_GAS_LIMIT, signer)
    }

    private suspend fun sign(
        messages: List<ProtoAny>,
        memo: String,
        gasLimit: Long,
        signer: Signer
    ): SignedTransaction {
        val signerAddress = Address(address.prefix, Utils.sha256Hash160(signer.publicKey))
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

        return SignedTransaction(txRaw, TxBuilder.txHash(txRaw), account.accountNumber, account.sequence)
    }

    // consumed only when the node is past the signed sequence; "got" ahead of "expected" is a
    // lagging node and the tx is still valid
    private fun BroadcastError.isSequenceConsumed(): Boolean {
        if (codespace != ThornodeApiProvider.SDK_CODESPACE || code != ThornodeApiProvider.CODE_WRONG_SEQUENCE) return false

        val match = SEQUENCE_MISMATCH.find(log) ?: return false
        val expected = match.groupValues[1].toLongOrNull() ?: return false
        val got = match.groupValues[2].toLongOrNull() ?: return false
        return got < expected
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

        // The account sequence this tx was signed with is already used — possibly by this very
        // tx if it was broadcast before. It can no longer be accepted; check transactionExists(hash).
        public class SequenceConsumed(cause: Throwable? = null) : SendError(cause)
    }

    companion object {
        // ~6s block time: 5 x 3s covers inclusion of an accepted tx
        private const val CONFIRMATION_ATTEMPTS = 5
        private const val CONFIRMATION_DELAY_MS = 3_000L

        // cosmos-sdk x/auth/ante sigverify
        private val SEQUENCE_MISMATCH = Regex("account sequence mismatch, expected (\\d+), got (\\d+)")
    }
}
