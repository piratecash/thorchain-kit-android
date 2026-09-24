package io.horizontalsystems.thorchainkit

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import io.horizontalsystems.thorchainkit.network.InvalidProviderResponse
import io.horizontalsystems.thorchainkit.network.Network
import io.horizontalsystems.thorchainkit.network.SenderFee
import io.horizontalsystems.thorchainkit.network.TxDetailsResponse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test
import java.math.BigInteger

class SenderFeeTest {

    private fun fee(json: String, sender: String, network: Network): BigInteger? {
        val tx = checkNotNull(Gson().fromJson(json, TxDetailsResponse::class.java).txResponse)
        return SenderFee.of(tx, sender, network.assetResolver)
    }

    private fun withoutField(json: String, field: String): String {
        val response = JsonParser.parseString(json).asJsonObject
        response.getAsJsonObject("tx_response").remove(field)
        return response.toString()
    }

    private fun withFirstMessage(json: String, edit: (JsonObject) -> Unit): String {
        val response = JsonParser.parseString(json).asJsonObject
        val messages = response.getAsJsonObject("tx_response").getAsJsonObject("tx").getAsJsonObject("body")
            .getAsJsonArray("messages")
        edit(messages[0].asJsonObject)
        return response.toString()
    }

    private fun assertMalformed(json: String, sender: String, network: Network) {
        assertThrows(InvalidProviderResponse::class.java) { fee(json, sender, network) }
    }

    @Test
    fun of_thorchainSend_returnsNativeFeeNotTheAmount() {
        val fee = fee(MainnetFixtures.THOR_SEND, "thor1t60f02r8jvzjrhtnjgfj4ne6rs5wjnejwmj7fh", Network.Mainnet)

        assertEquals(BigInteger("2000000"), fee)
    }

    @Test
    fun of_thorchainSwapDeposit_returnsNativeFee() {
        val fee = fee(MainnetFixtures.THOR_SWAP_DEPOSIT, "thor17hwqt302e5f2xm4h95ma8wuggqkvfzgvsnh5z9", Network.Mainnet)

        assertEquals(BigInteger("2000000"), fee)
    }

    @Test
    fun of_thorchainFailedDeposit_returnsChargedFee() {
        val fee = fee(MainnetFixtures.THOR_FAILED_DEPOSIT, "thor1z3e8pxs5fpgcdjpnn92y7xfv90enqm46qtxdl2", Network.Mainnet)

        assertEquals(BigInteger("2000000"), fee)
    }

    @Test
    fun of_mayaCacaoSend_sumsBase64FeeTransfers() {
        val fee = fee(MainnetFixtures.MAYA_CACAO_SEND, "maya1qc30hsy23lgf3hnkwypg3p4ecxw3yad94l2rm7", Network.MayaMainnet)

        assertEquals(BigInteger("2000000000"), fee)
    }

    @Test
    fun of_mayaTokenSend_returnsFeePaidInCacao() {
        val fee = fee(MainnetFixtures.MAYA_TOKEN_SEND, "maya1pf7gg2h9kdq7zuj58r7wk8py99awwj9lwvchdx", Network.MayaMainnet)

        assertEquals(BigInteger("2000000000"), fee)
    }

    @Test
    fun of_mayaUnchargedFailedSend_returnsNull() {
        assertNull(fee(MainnetFixtures.MAYA_UNCHARGED_SEND, "maya1569spcpfqpxpxuqj5mffkyarngx8dgu0uvsudx", Network.MayaMainnet))
    }

    @Test
    fun of_mayaTwoDeposits_returnsFeeOfEveryMessage() {
        val fee = fee(MainnetFixtures.MAYA_TWO_DEPOSITS, "maya1dl3yrfpedyr5jfr0r86s2apjltnjqgszmwsv8x", Network.MayaMainnet)

        assertEquals(BigInteger("4000000000"), fee)
    }

    @Test
    fun of_transactionOfAnotherSender_returnsNull() {
        assertNull(fee(MainnetFixtures.THOR_SEND, "thor166n4w5039meulfa3p6ydg60ve6ueac7tlt0jws", Network.Mainnet))
    }

    @Test
    fun of_missingCode_throwsMalformed() {
        assertMalformed(withoutField(MainnetFixtures.THOR_SEND, "code"), THOR_SENDER, Network.Mainnet)
    }

    @Test
    fun of_missingEvents_throwsMalformed() {
        assertMalformed(withoutField(MainnetFixtures.THOR_SEND, "events"), THOR_SENDER, Network.Mainnet)
    }

    @Test
    fun of_successWithoutMessages_throwsMalformed() {
        assertMalformed(withoutField(MainnetFixtures.THOR_SEND, "tx"), THOR_SENDER, Network.Mainnet)
    }

    @Test
    fun of_unparsableSenderCoins_throwsMalformed() {
        val json = MainnetFixtures.THOR_SEND.replace("\"2000000rune\"", "\"2000000.5rune\"")

        assertMalformed(json, THOR_SENDER, Network.Mainnet)
    }

    @Test
    fun of_brokenBase64Attribute_throwsMalformed() {
        val json = MainnetFixtures.MAYA_CACAO_SEND.replace(
            "\"bWF5YTFkaGV5Y2RldnEzOXFsa3hzMmE2d3V1enluNGFxeGh2ZTRoYzhzbQ==\"", "\"not base64!\""
        )

        assertMalformed(json, "maya1qc30hsy23lgf3hnkwypg3p4ecxw3yad94l2rm7", Network.MayaMainnet)
    }

    @Test
    fun of_sendWithoutAmount_throwsMalformed() {
        val json = withFirstMessage(MainnetFixtures.THOR_SEND) { it.remove("amount") }

        assertMalformed(json, THOR_SENDER, Network.Mainnet)
    }

    @Test
    fun of_sendCoinWithoutDenom_throwsMalformed() {
        val json = withFirstMessage(MainnetFixtures.THOR_SEND) {
            it.getAsJsonArray("amount")[0].asJsonObject.remove("denom")
        }

        assertMalformed(json, THOR_SENDER, Network.Mainnet)
    }

    @Test
    fun of_depositWithoutCoins_throwsMalformed() {
        val json = withFirstMessage(MainnetFixtures.MAYA_TWO_DEPOSITS) { it.remove("coins") }

        assertMalformed(json, MAYA_DEPOSITOR, Network.MayaMainnet)
    }

    @Test
    fun of_depositCoinWithoutAsset_throwsMalformed() {
        val json = withFirstMessage(MainnetFixtures.MAYA_TWO_DEPOSITS) {
            it.getAsJsonArray("coins")[0].asJsonObject.remove("asset")
        }

        assertMalformed(json, MAYA_DEPOSITOR, Network.MayaMainnet)
    }

    @Test
    fun of_negativeNativeMessageAmount_throwsMalformed() {
        val json = withFirstMessage(MainnetFixtures.THOR_SEND) {
            it.getAsJsonArray("amount")[0].asJsonObject.addProperty("amount", "-1")
        }

        assertMalformed(json, THOR_SENDER, Network.Mainnet)
    }

    @Test
    fun of_messageWithoutType_throwsMalformed() {
        val json = withFirstMessage(MainnetFixtures.THOR_SEND) { it.remove("@type") }

        assertMalformed(json, THOR_SENDER, Network.Mainnet)
    }

    @Test
    fun of_unknownMessageType_returnsNull() {
        val json = withFirstMessage(MainnetFixtures.THOR_SEND) { it.addProperty("@type", "/types.MsgUnknown") }

        assertNull(fee(json, THOR_SENDER, Network.Mainnet))
    }

    private companion object {
        const val THOR_SENDER = "thor1t60f02r8jvzjrhtnjgfj4ne6rs5wjnejwmj7fh"
        const val MAYA_DEPOSITOR = "maya1dl3yrfpedyr5jfr0r86s2apjltnjqgszmwsv8x"
    }
}
