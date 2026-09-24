package io.horizontalsystems.thorchainkit

import com.google.gson.JsonParser
import io.horizontalsystems.thorchainkit.network.InvalidProviderResponse
import io.horizontalsystems.thorchainkit.network.ThornodeApiProvider
import io.horizontalsystems.thorchainkit.network.TxByHashResponse
import io.horizontalsystems.thorchainkit.network.TxResponse
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.HttpException

class ThornodeApiProviderTest {

    private val baseAccountJson = """
        {
          "@type": "/cosmos.auth.v1beta1.BaseAccount",
          "address": "thor1gm00vwsfcp48enm4uv9e5dhm37jtd0ye27wrx0",
          "pub_key": null,
          "account_number": "123456",
          "sequence": "42"
        }
        """

    @Test
    fun parseAccountInfo_baseAccount() {
        val json = JsonParser.parseString(baseAccountJson).asJsonObject

        val accountInfo = ThornodeApiProvider.parseAccountInfo(json)

        assertEquals(123456L, accountInfo.accountNumber)
        assertEquals(42L, accountInfo.sequence)
    }

    @Test
    fun parseAccountInfo_moduleAccount() {
        // real payload shape returned for thor1dheycdevq39qlkxs2a6wuuzyn4aqxhve4qxtxt (reserve)
        val json = JsonParser.parseString(
            """
            {
              "@type": "/cosmos.auth.v1beta1.ModuleAccount",
              "base_account": {
                "address": "thor1dheycdevq39qlkxs2a6wuuzyn4aqxhve4qxtxt",
                "pub_key": null,
                "account_number": "8",
                "sequence": "0"
              },
              "name": "reserve",
              "permissions": []
            }
            """
        ).asJsonObject

        val accountInfo = ThornodeApiProvider.parseAccountInfo(json)

        assertEquals(8L, accountInfo.accountNumber)
        assertEquals(0L, accountInfo.sequence)
    }

    @Test
    fun parseAccountInfo_unsupportedType() {
        val json = JsonParser.parseString("""{"@type": "/cosmos.auth.v1beta1.SomethingElse"}""").asJsonObject

        assertThrows(IllegalArgumentException::class.java) {
            ThornodeApiProvider.parseAccountInfo(json)
        }
    }

    @Test
    fun parseAccountInfo_addressMatch() {
        val json = JsonParser.parseString(baseAccountJson).asJsonObject

        val accountInfo = ThornodeApiProvider.parseAccountInfo(json, "thor1gm00vwsfcp48enm4uv9e5dhm37jtd0ye27wrx0")

        assertEquals(123456L, accountInfo.accountNumber)
    }

    @Test
    fun parseAccountInfo_addressMismatch() {
        // the response must be about the queried account — a provider answering for a
        // different address must be rejected, not signed over
        val json = JsonParser.parseString(baseAccountJson).asJsonObject

        assertThrows(InvalidProviderResponse::class.java) {
            ThornodeApiProvider.parseAccountInfo(json, "thor1dheycdevq39qlkxs2a6wuuzyn4aqxhve4qxtxt")
        }
    }

    @Test
    fun parseAccountInfo_missingSequence() {
        val json = JsonParser.parseString(
            """
            {
              "@type": "/cosmos.auth.v1beta1.BaseAccount",
              "address": "thor1gm00vwsfcp48enm4uv9e5dhm37jtd0ye27wrx0",
              "account_number": "123456"
            }
            """
        ).asJsonObject

        assertThrows(InvalidProviderResponse::class.java) {
            ThornodeApiProvider.parseAccountInfo(json)
        }
    }

    @Test
    fun transactionExists_code0_returnsTrue() {
        assertTrue(runBlocking { providerAnswering(code = 0).transactionExists(HASH) })
    }

    @Test
    fun transactionExists_failedCode_returnsFalse() {
        assertFalse(runBlocking { providerAnswering(code = 5).transactionExists(HASH) })
    }

    @Test
    fun transactionExists_missingCode_throwsInvalidProviderResponse() {
        assertThrows(InvalidProviderResponse::class.java) {
            runBlocking { providerAnswering(code = null).transactionExists(HASH) }
        }
    }

    @Test
    fun transactionExists_notFound_returnsFalse() {
        assertFalse(runBlocking { providerFailing(FakeThornodeApi.httpException(404)).transactionExists(HASH) })
    }

    @Test
    fun transactionExists_serverError_throws() {
        assertThrows(HttpException::class.java) {
            runBlocking { providerFailing(FakeThornodeApi.httpException(503)).transactionExists(HASH) }
        }
    }

    @Test
    fun transactionExists_firstProviderMissingCode_failsOverToSecond() {
        var firstCalls = 0
        var secondCalls = 0
        val malformed = object : FakeThornodeApi() {
            override suspend fun transaction(hash: String): TxByHashResponse {
                firstCalls++
                return TxByHashResponse(TxResponse("100", hash, null, null, null))
            }
        }
        val healthy = object : FakeThornodeApi() {
            override suspend fun transaction(hash: String): TxByHashResponse {
                secondCalls++
                return TxByHashResponse(TxResponse("100", hash, null, 0, null))
            }
        }

        val exists = runBlocking { ThornodeApiProvider(listOf(malformed, healthy)).transactionExists(HASH) }

        assertTrue(exists)
        assertEquals(1, firstCalls)
        assertEquals(1, secondCalls)
    }

    private fun providerAnswering(code: Int?): ThornodeApiProvider = ThornodeApiProvider(listOf(object : FakeThornodeApi() {
        override suspend fun transaction(hash: String): TxByHashResponse =
            TxByHashResponse(TxResponse("100", hash, null, code, null))
    }))

    private fun providerFailing(error: Throwable): ThornodeApiProvider = ThornodeApiProvider(listOf(object : FakeThornodeApi() {
        override suspend fun transaction(hash: String): TxByHashResponse = throw error
    }))

    private companion object {
        const val HASH = "E3B0C44298FC1C149AFBF4C8996FB92427AE41E4649B934CA495991B7852B855"
    }
}
