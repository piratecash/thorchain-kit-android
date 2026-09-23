package io.horizontalsystems.thorchainkit.network

import com.google.gson.JsonObject
import io.horizontalsystems.thorchainkit.models.AccountInfo
import io.horizontalsystems.thorchainkit.models.Address
import io.horizontalsystems.thorchainkit.models.DenomBalance
import io.horizontalsystems.thorchainkit.transaction.TxBuilder
import kotlinx.coroutines.CancellationException
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.math.BigInteger
import java.net.URL
import java.util.Base64

// the api list is the injection point for tests; real instances come from `create`.
// it cannot be a second constructor: List<URL> and List<ThornodeApi> erase to the
// same JVM signature
class ThornodeApiProvider internal constructor(
    private val apis: List<ThornodeApi>,
    // API path segment for the protocol module, e.g. "thorchain" / "mayachain"
    private val protocolPath: String = "thorchain"
) {

    suspend fun fetchBalances(address: Address): List<DenomBalance> =
        withFailover { api ->
            val balances = api.balances(address.toString()).balances
                ?: throw InvalidProviderResponse("balances: missing 'balances' field")

            balances.map {
                val denom = it.denom
                    ?: throw InvalidProviderResponse("balances: missing denom")
                DenomBalance(denom, parseAmount(it.amount, "balances"))
            }
        }

    // null for accounts the chain has not seen yet (created on first receive)
    suspend fun fetchAccount(address: Address): AccountInfo? =
        withFailover { api ->
            try {
                val account = api.account(address.toString()).account
                    ?: throw InvalidProviderResponse("account: missing 'account' field")

                parseAccountInfo(account, address.toString())
            } catch (error: HttpException) {
                if (error.code() == 404) null else throw error
            }
        }

    suspend fun fetchNativeTxFee(): BigInteger =
        withFailover { api ->
            // NativeTransactionFee is in the chain's base units (e.g. 2000000 = 0.02 RUNE at
            // 8 decimals; 2000000000 = 0.2 CACAO at 10 decimals) — same key on both chains
            val fee = api.constants(protocolPath).int64Values?.nativeTransactionFee
                ?: throw InvalidProviderResponse("constants: missing NativeTransactionFee")
            if (fee < 0) throw InvalidProviderResponse("constants: negative fee: $fee")
            BigInteger.valueOf(fee)
        }

    suspend fun fetchLastBlockHeight(): Long =
        withFailover { api ->
            val entry = api.lastBlock(protocolPath).firstOrNull()
                ?: throw InvalidProviderResponse("lastblock: empty response")

            // the height is keyed by the protocol module name, which equals protocolPath
            // ("thorchain" / "mayachain") — a fixed `thorchain` field would read null on Maya
            val height = entry.get(protocolPath)
            if (height == null || height.isJsonNull) {
                throw InvalidProviderResponse("lastblock: missing '$protocolPath' height")
            }
            try {
                height.asLong
            } catch (error: NumberFormatException) {
                throw InvalidProviderResponse("lastblock: invalid height: $height")
            }
        }

    suspend fun fetchChainId(): String =
        withFailover { api ->
            api.nodeInfo().defaultNodeInfo?.network?.takeIf { it.isNotEmpty() }
                ?: throw InvalidProviderResponse("node_info: missing network")
        }

    // Broadcasting is not idempotent-safe to *report* on: the request can reach the node
    // and still fail locally (timeout, gateway error). Semantics:
    //  - CheckTx rejection (code != 0) is a definitive answer: throw BroadcastError, no failover
    //  - "tx already in mempool cache" means an earlier attempt reached the node: success
    //  - anything else is ambiguous: throw BroadcastAmbiguousError carrying the locally
    //    computed tx hash so the caller can resolve it via fetchTransaction — the tx may
    //    have been accepted, and reporting a plain failure invites a wallet-level retry
    //    that would be a second real payment
    suspend fun broadcast(txRaw: ByteArray): String {
        val expectedHash = TxBuilder.txHash(txRaw)
        val request = BroadcastRequest(txBytes = Base64.getEncoder().encodeToString(txRaw))
        var ambiguousError: Throwable? = null

        apis.forEach { api ->
            try {
                val response = api.broadcast(request).txResponse
                    ?: throw InvalidProviderResponse("broadcast: missing tx_response")
                val code = response.code
                    ?: throw InvalidProviderResponse("broadcast: missing code")

                if (code == 0) return expectedHash

                // this exact tx is already known to the node — a previous attempt made it
                if (code == CODE_TX_IN_MEMPOOL_CACHE && response.codespace == SDK_CODESPACE) {
                    return expectedHash
                }

                throw BroadcastError(code, response.rawLog ?: "")
            } catch (error: BroadcastError) {
                // If an earlier attempt already failed ambiguously, this rejection may be a
                // side effect of that attempt having succeeded (e.g. "sequence mismatch"
                // once the tx committed and left the mempool cache) — it must stay
                // ambiguous so the caller resolves the true outcome via tx lookup.
                if (ambiguousError != null) throw BroadcastAmbiguousError(expectedHash, error)
                throw error
            } catch (error: HttpException) {
                if (error.code() < 500) {
                    if (ambiguousError != null) throw BroadcastAmbiguousError(expectedHash, error)
                    throw error
                }
                ambiguousError = error
            } catch (error: CancellationException) {
                // cancelled mid-broadcast: the attempt may have reached the node —
                // surface as ambiguous, never as a plain cancellation the caller ignores
                throw BroadcastAmbiguousError(expectedHash, error)
            } catch (error: Throwable) {
                ambiguousError = error
            }
        }

        throw BroadcastAmbiguousError(expectedHash, ambiguousError)
    }

    // null while the transaction is not yet included in a block
    suspend fun fetchTransaction(hash: String): TxResponse? =
        withFailover { api ->
            try {
                api.transaction(hash).txResponse
                    ?: throw InvalidProviderResponse("tx by hash: missing tx_response")
            } catch (error: HttpException) {
                if (error.code() == 404) null else throw error
            }
        }

    private suspend fun <T> withFailover(block: suspend (ThornodeApi) -> T): T {
        var lastError: Throwable? = null

        apis.forEach { api ->
            try {
                return block(api)
            } catch (error: HttpException) {
                // client errors are definitive answers, not provider outages
                if (error.code() < 500) throw error
                lastError = error
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                lastError = error
            }
        }

        throw lastError ?: IllegalStateException("No thornode providers configured")
    }

    companion object {

        fun create(baseUrls: List<URL>, protocolPath: String = "thorchain") = ThornodeApiProvider(
            baseUrls.map {
                Retrofit.Builder()
                    .baseUrl(it.toString())
                    .client(ApiClient.build())
                    .addConverterFactory(GsonConverterFactory.create())
                    .build()
                    .create(ThornodeApi::class.java)
            },
            protocolPath
        )

        // cosmos-sdk root codespace, error 19: ErrTxInMempoolCache
        const val SDK_CODESPACE = "sdk"
        const val CODE_TX_IN_MEMPOOL_CACHE = 19

        private fun parseAmount(value: String?, context: String): BigInteger {
            val string = value ?: throw InvalidProviderResponse("$context: missing amount")
            val amount = try {
                BigInteger(string)
            } catch (error: NumberFormatException) {
                throw InvalidProviderResponse("$context: invalid amount: $string")
            }
            if (amount.signum() < 0) {
                throw InvalidProviderResponse("$context: negative amount: $string")
            }
            return amount
        }

        fun parseAccountInfo(account: JsonObject, expectedAddress: String? = null): AccountInfo {
            val type = account.get("@type")?.asString ?: ""
            val fields = if (account.has("base_account")) {
                account.getAsJsonObject("base_account")
            } else {
                require(type.endsWith("BaseAccount")) { "Unsupported account type: $type" }
                account
            }

            // the response must be about the account we asked for
            if (expectedAddress != null) {
                val address = fields.get("address")?.asString
                if (address != expectedAddress) {
                    throw InvalidProviderResponse("account: address mismatch: $address, expected: $expectedAddress")
                }
            }

            return AccountInfo(
                accountNumber = fields.get("account_number")?.asString?.toLongOrNull()
                    ?: throw InvalidProviderResponse("account: missing account_number"),
                sequence = fields.get("sequence")?.asString?.toLongOrNull()
                    ?: throw InvalidProviderResponse("account: missing sequence")
            )
        }
    }
}

class BroadcastError(val code: Int, val log: String) : Throwable() {
    override val message: String
        get() = "Broadcast failed with code: $code, log: $log"
}

// the broadcast request failed locally, but the node may still have accepted the tx
class BroadcastAmbiguousError(val txHash: String, cause: Throwable?) : Throwable(cause) {
    override val message: String
        get() = "Broadcast result unknown for tx $txHash (${cause?.message})"
}

// the provider returned a response that does not match the expected shape — treated
// as a provider failure (retryable via failover), never as data
class InvalidProviderResponse(override val message: String) : Throwable()
