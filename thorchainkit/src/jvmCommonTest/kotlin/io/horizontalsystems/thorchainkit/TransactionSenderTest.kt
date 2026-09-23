package io.horizontalsystems.thorchainkit

import com.google.gson.JsonParser
import io.horizontalsystems.thorchainkit.models.Address
import io.horizontalsystems.thorchainkit.network.AccountResponse
import io.horizontalsystems.thorchainkit.network.BroadcastError
import io.horizontalsystems.thorchainkit.network.BroadcastRequest
import io.horizontalsystems.thorchainkit.network.BroadcastResponse
import io.horizontalsystems.thorchainkit.network.InvalidProviderResponse
import io.horizontalsystems.thorchainkit.network.Network
import io.horizontalsystems.thorchainkit.network.ThornodeApiProvider
import io.horizontalsystems.thorchainkit.network.TxByHashResponse
import io.horizontalsystems.thorchainkit.network.TxResponse
import io.horizontalsystems.thorchainkit.transaction.Signer
import io.horizontalsystems.thorchainkit.transaction.TransactionSender
import io.horizontalsystems.thorchainkit.transaction.TxBuilder
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigInteger
import java.io.IOException
import java.util.Base64

// key/address pair from SignerTest (BIP39 "abandon ... about" at m/44'/931'/0'/0/0)
private const val ADDRESS = "thor1gm00vwsfcp48enm4uv9e5dhm37jtd0ye27wrx0"

// Every fake below inherits nodeInfo() = throw NotImplementedError: this proves the send
// path never asks the node for the chain-id (it is pinned on Network), so a malicious
// endpoint cannot trick the kit into signing for a different network.
private open class SendFakeApi : FakeThornodeApi() {
    override suspend fun account(address: String): AccountResponse =
        AccountResponse(
            JsonParser.parseString(
                """
                {
                  "@type": "/cosmos.auth.v1beta1.BaseAccount",
                  "address": "$ADDRESS",
                  "account_number": "12345",
                  "sequence": "7"
                }
                """
            ).asJsonObject
        )
}

class TransactionSenderTest {

    private val privateKey = BigInteger("cd48c8b23a5d619cb67b7a4886d25127acf2e8c023e42a1e9ae14c6194532aa9", 16)
    private val signer = Signer(privateKey)
    private val address = Address.fromString(ADDRESS, Network.Mainnet)

    private fun sender(api: FakeThornodeApi): TransactionSender =
        TransactionSender(
            address = address,
            network = Network.Mainnet,
            thornodeApiProvider = ThornodeApiProvider(listOf(api)),
            confirmationAttempts = 3,
            confirmationDelayMs = 1
        )

    @Test
    fun send_success_returnsHashOfBroadcastBytes() {
        var broadcastTxHash: String? = null
        val api = object : SendFakeApi() {
            override suspend fun broadcast(request: BroadcastRequest): BroadcastResponse {
                broadcastCalls++
                broadcastTxHash = TxBuilder.txHash(Base64.getDecoder().decode(request.txBytes))
                return BroadcastResponse(TxResponse("0", broadcastTxHash, null, 0, null))
            }
        }

        val hash = runBlocking { sender(api).send(address, BigInteger.ONE, "rune", null, signer) }

        assertEquals(broadcastTxHash, hash)
        assertEquals(64, hash.length)
        assertEquals(1, api.broadcastCalls)
    }

    @Test
    fun send_ambiguousBroadcast_resolvedViaTxLookup() {
        // the broadcast request dies locally, but the tx made it: the sender must
        // recover the success by looking the tx up by its locally computed hash
        var lookedUpHash: String? = null
        val api = object : SendFakeApi() {
            override suspend fun broadcast(request: BroadcastRequest): BroadcastResponse {
                broadcastCalls++
                throw IOException("connection reset")
            }

            override suspend fun transaction(hash: String): TxByHashResponse {
                lookedUpHash = hash
                return TxByHashResponse(TxResponse("100", hash, null, 0, null))
            }
        }

        val hash = runBlocking { sender(api).send(address, BigInteger.ONE, "rune", null, signer) }

        assertEquals(lookedUpHash, hash)
        assertEquals(64, hash.length)
    }

    @Test
    fun send_ambiguousBroadcast_unresolved_throwsPossiblyAccepted() {
        // outcome unknown: the integrator must get a typed error carrying the tx hash,
        // never a plain failure that invites a blind retry (= second real payment)
        val api = object : SendFakeApi() {
            override suspend fun broadcast(request: BroadcastRequest): BroadcastResponse {
                broadcastCalls++
                throw IOException("timeout")
            }

            override suspend fun transaction(hash: String): TxByHashResponse =
                throw httpException(404)
        }

        val error = assertThrows(TransactionSender.SendError.PossiblyAccepted::class.java) {
            runBlocking { sender(api).send(address, BigInteger.ONE, "rune", null, signer) }
        }

        assertEquals(64, error.txHash.length)
        assertTrue(error.txHash.all { it.isDigit() || it in 'A'..'F' })
    }

    @Test
    fun send_ambiguousBroadcast_txFailedOnChain_throwsBroadcastError() {
        val api = object : SendFakeApi() {
            override suspend fun broadcast(request: BroadcastRequest): BroadcastResponse =
                throw IOException("timeout")

            override suspend fun transaction(hash: String): TxByHashResponse =
                TxByHashResponse(TxResponse("100", hash, null, 5, "insufficient funds"))
        }

        val error = assertThrows(BroadcastError::class.java) {
            runBlocking { sender(api).send(address, BigInteger.ONE, "rune", null, signer) }
        }

        assertEquals(5, error.code)
    }

    @Test
    fun send_ambiguousBroadcast_malformedLookup_throwsPossiblyAccepted() {
        // during resolution, a tx_response with a missing `code` must never be read
        // as confirmed success
        val api = object : SendFakeApi() {
            override suspend fun broadcast(request: BroadcastRequest): BroadcastResponse =
                throw IOException("timeout")

            override suspend fun transaction(hash: String): TxByHashResponse =
                TxByHashResponse(TxResponse("100", hash, null, null, null))
        }

        assertThrows(TransactionSender.SendError.PossiblyAccepted::class.java) {
            runBlocking { sender(api).send(address, BigInteger.ONE, "rune", null, signer) }
        }
    }

    @Test
    fun send_ambiguousThenSequenceMismatch_resolvedAsSuccessViaLookup() {
        // end-to-end double-payment guard: broadcast dies locally, retry path reports
        // "sequence mismatch" (because the first attempt committed), and the tx lookup
        // finds the committed tx — the send must be reported as SUCCESS with the hash
        var attempts = 0
        val api = object : SendFakeApi() {
            override suspend fun broadcast(request: BroadcastRequest): BroadcastResponse {
                attempts++
                if (attempts == 1) throw IOException("timeout")
                return BroadcastResponse(TxResponse(null, null, "sdk", 32, "account sequence mismatch"))
            }

            override suspend fun transaction(hash: String): TxByHashResponse =
                TxByHashResponse(TxResponse("100", hash, null, 0, null))
        }

        val provider = ThornodeApiProvider(listOf(api, api))
        val sender = TransactionSender(
            address = address,
            network = Network.Mainnet,
            thornodeApiProvider = provider,
            confirmationAttempts = 3,
            confirmationDelayMs = 1
        )

        val hash = runBlocking { sender.send(address, BigInteger.ONE, "rune", null, signer) }

        assertEquals(64, hash.length)
        assertEquals(2, attempts)
    }

    @Test
    fun send_accountResponseForWrongAddress_rejected() {
        // account data feeds the SignDoc — a provider answering for a different
        // address must not be signed over
        val api = object : SendFakeApi() {
            override suspend fun account(address: String): AccountResponse =
                AccountResponse(
                    JsonParser.parseString(
                        """
                        {
                          "@type": "/cosmos.auth.v1beta1.BaseAccount",
                          "address": "thor1dheycdevq39qlkxs2a6wuuzyn4aqxhve4qxtxt",
                          "account_number": "8",
                          "sequence": "0"
                        }
                        """
                    ).asJsonObject
                )
        }

        assertThrows(InvalidProviderResponse::class.java) {
            runBlocking { sender(api).send(address, BigInteger.ONE, "rune", null, signer) }
        }
    }

    @Test
    fun send_signerMismatch() {
        val watchOnly = Address.fromString("thor1dheycdevq39qlkxs2a6wuuzyn4aqxhve4qxtxt", Network.Mainnet)
        val watchSender = TransactionSender(
            address = watchOnly,
            network = Network.Mainnet,
            thornodeApiProvider = ThornodeApiProvider(listOf(FakeThornodeApi())),
            confirmationAttempts = 1,
            confirmationDelayMs = 1
        )

        assertThrows(TransactionSender.SendError.SignerMismatch::class.java) {
            runBlocking { watchSender.send(address, BigInteger.ONE, "rune", null, signer) }
        }
    }
}
