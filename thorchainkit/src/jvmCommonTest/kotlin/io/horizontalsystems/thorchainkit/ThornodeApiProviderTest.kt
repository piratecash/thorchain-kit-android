package io.horizontalsystems.thorchainkit

import com.google.gson.JsonParser
import io.horizontalsystems.thorchainkit.network.InvalidProviderResponse
import io.horizontalsystems.thorchainkit.network.ThornodeApiProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

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
}
