package io.horizontalsystems.thorchainkit

import io.horizontalsystems.thorchainkit.network.BroadcastAmbiguousError
import io.horizontalsystems.thorchainkit.network.BroadcastError
import io.horizontalsystems.thorchainkit.network.BroadcastRequest
import io.horizontalsystems.thorchainkit.network.BroadcastResponse
import io.horizontalsystems.thorchainkit.network.ThornodeApiProvider
import io.horizontalsystems.thorchainkit.network.TxResponse
import io.horizontalsystems.thorchainkit.transaction.TxBuilder
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class BroadcastSafetyTest {

    private val txRaw = byteArrayOf(1, 2, 3, 4)
    private val expectedHash = TxBuilder.txHash(txRaw)

    private fun txResponse(code: Int, codespace: String? = null) = TxResponse(
        height = "0",
        txhash = expectedHash,
        codespace = codespace,
        code = code,
        rawLog = "log"
    )

    @Test
    fun txHash_isUppercaseSha256() {
        // SHA-256 of the empty byte string, uppercased
        assertEquals(
            "E3B0C44298FC1C149AFBF4C8996FB92427AE41E4649B934CA495991B7852B855",
            TxBuilder.txHash(ByteArray(0))
        )
        assertEquals(64, expectedHash.length)
    }

    @Test
    fun broadcast_success_returnsLocallyComputedHash() {
        val api = object : FakeThornodeApi() {
            override suspend fun broadcast(request: BroadcastRequest): BroadcastResponse {
                broadcastCalls++
                return BroadcastResponse(txResponse(code = 0))
            }
        }

        val hash = runBlocking { ThornodeApiProvider(listOf(api)).broadcast(txRaw) }

        assertEquals(expectedHash, hash)
        assertEquals(1, api.broadcastCalls)
    }

    @Test
    fun broadcast_checkTxRejection_isDefinitive_noFailover() {
        // a CheckTx rejection is an answer, not an outage — the tx must NOT be
        // re-broadcast to the next provider
        val api1 = object : FakeThornodeApi() {
            override suspend fun broadcast(request: BroadcastRequest): BroadcastResponse {
                broadcastCalls++
                return BroadcastResponse(txResponse(code = 5))
            }
        }
        val api2 = object : FakeThornodeApi() {
            override suspend fun broadcast(request: BroadcastRequest): BroadcastResponse {
                broadcastCalls++
                return BroadcastResponse(txResponse(code = 0))
            }
        }

        val error = assertThrows(BroadcastError::class.java) {
            runBlocking { ThornodeApiProvider(listOf(api1, api2)).broadcast(txRaw) }
        }

        assertEquals(5, error.code)
        assertEquals(1, api1.broadcastCalls)
        assertEquals(0, api2.broadcastCalls)
    }

    @Test
    fun broadcast_txAlreadyInMempool_isSuccess() {
        // first provider times out AFTER the node accepted the tx; the failover
        // provider reports "tx already in mempool cache" — the send succeeded
        val api1 = object : FakeThornodeApi() {
            override suspend fun broadcast(request: BroadcastRequest): BroadcastResponse {
                broadcastCalls++
                throw IOException("timeout")
            }
        }
        val api2 = object : FakeThornodeApi() {
            override suspend fun broadcast(request: BroadcastRequest): BroadcastResponse {
                broadcastCalls++
                return BroadcastResponse(
                    txResponse(
                        code = ThornodeApiProvider.CODE_TX_IN_MEMPOOL_CACHE,
                        codespace = ThornodeApiProvider.SDK_CODESPACE
                    )
                )
            }
        }

        val hash = runBlocking { ThornodeApiProvider(listOf(api1, api2)).broadcast(txRaw) }

        assertEquals(expectedHash, hash)
    }

    @Test
    fun broadcast_allProvidersAmbiguous_throwsAmbiguousWithHash() {
        val api = object : FakeThornodeApi() {
            override suspend fun broadcast(request: BroadcastRequest): BroadcastResponse {
                broadcastCalls++
                throw IOException("timeout")
            }
        }

        val error = assertThrows(BroadcastAmbiguousError::class.java) {
            runBlocking { ThornodeApiProvider(listOf(api)).broadcast(txRaw) }
        }

        // the caller gets the hash it needs to resolve the outcome
        assertEquals(expectedHash, error.txHash)
        assertTrue(error.cause is IOException)
    }

    @Test
    fun broadcast_failsOverOn5xx() {
        val api1 = object : FakeThornodeApi() {
            override suspend fun broadcast(request: BroadcastRequest): BroadcastResponse {
                broadcastCalls++
                throw httpException(502)
            }
        }
        val api2 = object : FakeThornodeApi() {
            override suspend fun broadcast(request: BroadcastRequest): BroadcastResponse {
                broadcastCalls++
                return BroadcastResponse(txResponse(code = 0))
            }
        }

        val hash = runBlocking { ThornodeApiProvider(listOf(api1, api2)).broadcast(txRaw) }

        assertEquals(expectedHash, hash)
        assertEquals(1, api2.broadcastCalls)
    }

    @Test
    fun broadcast_missingTxResponse_isAmbiguous_notSuccess() {
        // a gateway 200 with an unexpected body must never be read as success
        // (with a stripped/missing `code` field the old parsing defaulted to 0)
        val api = object : FakeThornodeApi() {
            override suspend fun broadcast(request: BroadcastRequest): BroadcastResponse {
                broadcastCalls++
                return BroadcastResponse(null)
            }
        }

        val error = assertThrows(BroadcastAmbiguousError::class.java) {
            runBlocking { ThornodeApiProvider(listOf(api)).broadcast(txRaw) }
        }

        assertEquals(expectedHash, error.txHash)
    }

    @Test
    fun broadcast_ambiguousThenRejected_staysAmbiguous() {
        // provider 1 times out (tx may have committed); provider 2 then rejects with
        // "sequence mismatch" — which is exactly what happens when the first attempt
        // DID commit. This must stay ambiguous, not become a definitive failure that
        // invites a wallet-level retry (= second real payment).
        val api1 = object : FakeThornodeApi() {
            override suspend fun broadcast(request: BroadcastRequest): BroadcastResponse {
                broadcastCalls++
                throw IOException("timeout")
            }
        }
        val api2 = object : FakeThornodeApi() {
            override suspend fun broadcast(request: BroadcastRequest): BroadcastResponse {
                broadcastCalls++
                return BroadcastResponse(txResponse(code = 32, codespace = "sdk"))
            }
        }

        val error = assertThrows(BroadcastAmbiguousError::class.java) {
            runBlocking { ThornodeApiProvider(listOf(api1, api2)).broadcast(txRaw) }
        }

        assertEquals(expectedHash, error.txHash)
        assertTrue(error.cause is BroadcastError)
    }

    @Test
    fun broadcast_ambiguousThen4xx_staysAmbiguous() {
        val api1 = object : FakeThornodeApi() {
            override suspend fun broadcast(request: BroadcastRequest): BroadcastResponse {
                broadcastCalls++
                throw IOException("timeout")
            }
        }
        val api2 = object : FakeThornodeApi() {
            override suspend fun broadcast(request: BroadcastRequest): BroadcastResponse {
                broadcastCalls++
                throw httpException(429)
            }
        }

        val error = assertThrows(BroadcastAmbiguousError::class.java) {
            runBlocking { ThornodeApiProvider(listOf(api1, api2)).broadcast(txRaw) }
        }

        assertEquals(expectedHash, error.txHash)
    }

    @Test
    fun broadcast_missingCode_isAmbiguous_notSuccess() {
        val api = object : FakeThornodeApi() {
            override suspend fun broadcast(request: BroadcastRequest): BroadcastResponse {
                broadcastCalls++
                return BroadcastResponse(TxResponse(null, null, null, null, null))
            }
        }

        assertThrows(BroadcastAmbiguousError::class.java) {
            runBlocking { ThornodeApiProvider(listOf(api)).broadcast(txRaw) }
        }
    }
}
