package io.horizontalsystems.thorchainkit

import com.google.gson.JsonParser
import cosmos.crypto.secp256k1.Keys
import cosmos.tx.signing.v1beta1.Signing.SignMode
import cosmos.tx.v1beta1.TxOuterClass
import fr.acinq.secp256k1.Secp256k1
import io.horizontalsystems.hdwalletkit.Mnemonic
import io.horizontalsystems.hdwalletkit.Utils
import io.horizontalsystems.thorchainkit.models.Address
import io.horizontalsystems.thorchainkit.models.Asset
import io.horizontalsystems.thorchainkit.network.AccountResponse
import io.horizontalsystems.thorchainkit.network.BroadcastRequest
import io.horizontalsystems.thorchainkit.network.BroadcastResponse
import io.horizontalsystems.thorchainkit.network.Network
import io.horizontalsystems.thorchainkit.network.ThornodeApiProvider
import io.horizontalsystems.thorchainkit.network.TxResponse
import io.horizontalsystems.thorchainkit.transaction.Signer
import io.horizontalsystems.thorchainkit.transaction.ThorchainExternalSigner
import io.horizontalsystems.thorchainkit.transaction.TransactionSender
import io.horizontalsystems.thorchainkit.transaction.TransactionSender.SendError
import io.horizontalsystems.thorchainkit.transaction.TxBuilder
import io.horizontalsystems.thorchainkit.transaction.TxMessage
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigInteger
import java.util.Base64

/**
 * Expected sign docs: the shape of thornode app/params/amino_test.go (SIGN_MODE_LEGACY_AMINO_JSON) with thor addresses,
 * and docs produced by python json.dumps(sort_keys=True) with RFC 6979 low-S signatures of the speculos seed key.
 */
class AminoSigningTest {
    private val speculosMnemonic = "glory promote mansion idle axis finger extra february uncover one trip resource " +
        "lawn turtle enact monster seven myth punch hobby comfort wild raise skin"
    private val privateKey = Signer.privateKey(Mnemonic().toSeed(speculosMnemonic.split(" ")), Network.Mainnet)
    private val device = FakeDevice(privateKey)
    private val address = Address.fromPublicKey(device.publicKey, Network.Mainnet)
    private val other = Address.fromString("thor1mwyrp6lj85swy5e5g4hjlaacm33g6rw3p4qmq4", Network.Mainnet)

    @Test
    fun publicKeyAddress_speculosKey_matchesLedgerAddress() {
        assertEquals("0335f35211b872a0a6a5e0cb54885e82e8d71c7c945111d6f782db1254ba2a2d8e", device.publicKey.toHexString())
        assertEquals("thor19hzdfmktztey2vuqpa3tv7dqe0kw3u24km6yca", address.toString())
    }

    @Test
    fun buildAminoSigned_msgSend_signsLedgerVectorDoc() {
        val raw = build(TxMessage.Send(address, other, BigInteger.valueOf(150_000_000), "rune"), memo = "ledger-kit test")

        assertEquals(
            """{"account_number":"123","chain_id":"thorchain-1","fee":{"amount":[],"gas":"50000000"},"memo":"ledger-kit test","msgs":[{"type":"thorchain/MsgSend","value":{"amount":[{"amount":"150000000","denom":"rune"}],"from_address":"thor19hzdfmktztey2vuqpa3tv7dqe0kw3u24km6yca","to_address":"thor1mwyrp6lj85swy5e5g4hjlaacm33g6rw3p4qmq4"}}],"sequence":"456"}""",
            device.signDocs.single(),
        )
        assertEquals(
            "2f25b7c73bcdf0aa48a064352b7b4b19452a99d1cf508acdfca52e58c4548f2c64831512675ac3cf3e9bf0c724dfdc0ec924c11da493b02fc20857f0a46a1d01",
            TxOuterClass.TxRaw.parseFrom(raw).getSignatures(0).toByteArray().toHexString(),
        )
    }

    @Test
    fun buildAminoSigned_msgDeposit_signsLedgerVectorDoc() {
        val memo = "=:BTC.BTC:bc1qar0srrr7xfkvy5l643lydnw9re59gtzzwf5mdq:0/1/0"

        val raw = build(TxMessage.Deposit(Asset.Rune, BigInteger.valueOf(100_000_000), memo, address))

        assertEquals(
            """{"account_number":"123","chain_id":"thorchain-1","fee":{"amount":[],"gas":"50000000"},"memo":"","msgs":[{"type":"thorchain/MsgDeposit","value":{"coins":[{"amount":"100000000","asset":"THOR.RUNE"}],"memo":"$memo","signer":"thor19hzdfmktztey2vuqpa3tv7dqe0kw3u24km6yca"}}],"sequence":"456"}""",
            device.signDocs.single(),
        )
        assertEquals(
            "3f8124310136b2a775fcb4aac8113700f87b81f83c62b4cd09cd30e51c1cad3a5a256f14226d158d1055a4b42fb5c9852f995d7ef078c2b7a04b01b92bb4cec0",
            TxOuterClass.TxRaw.parseFrom(raw).getSignatures(0).toByteArray().toHexString(),
        )
    }

    @Test
    fun buildAminoSigned_thornodeVectors_sameSignDoc() {
        val sender = address.toString()
        val recipient = other.toString()
        val node = "NODE:thor1a69c00hnmcy5d4taqlv2ljzgaxnfshzrxzscam"
        val eth = Asset("ETH", "ETH", "ETH")

        build(TxMessage.Send(address, other, BigInteger.valueOf(100), "rune"), "memo", 200_000, BigInteger.valueOf(100))
        build(TxMessage.Deposit(eth, BigInteger.valueOf(100_000_000), node, address), "memo", 200_000, BigInteger.valueOf(100))

        assertEquals(
            listOf(
                """{"account_number":"123","chain_id":"thorchain-1","fee":{"amount":[{"amount":"100","denom":"rune"}],"gas":"200000"},"memo":"memo","msgs":[{"type":"thorchain/MsgSend","value":{"amount":[{"amount":"100","denom":"rune"}],"from_address":"$sender","to_address":"$recipient"}}],"sequence":"456"}""",
                """{"account_number":"123","chain_id":"thorchain-1","fee":{"amount":[{"amount":"100","denom":"rune"}],"gas":"200000"},"memo":"memo","msgs":[{"type":"thorchain/MsgDeposit","value":{"coins":[{"amount":"100000000","asset":"ETH.ETH"}],"memo":"$node","signer":"$sender"}}],"sequence":"456"}""",
            ),
            device.signDocs,
        )
    }

    @Test
    fun buildAminoSigned_assetKinds_thornodeAssetNotation() {
        listOf("BTC-BTC", "BTC/BTC", "BTC~BTC", "ETH.USDC-0XA0B86991C6218B36C1D19D4A2E9EB0CE3606EB48").forEach { asset ->
            build(TxMessage.Deposit(Asset.fromString(asset), BigInteger.ONE, "=:THOR.RUNE:$address", address))
        }

        assertEquals(
            listOf("BTC-BTC", "BTC/BTC", "BTC~BTC", "ETH.USDC-0XA0B86991C6218B36C1D19D4A2E9EB0CE3606EB48"),
            device.signDocs.map { JsonParser.parseString(it).asJsonObject.depositCoin().get("asset").asString },
        )
    }

    @Test
    fun buildAminoSigned_memoWithHtmlAndControlCharacters_escapedAsGo() {
        build(TxMessage.Send(address, other, BigInteger.ONE, "rune"), memo = "a<b>&\"c\\\n\t\b\u000c\u0001\u2028é")

        assertTrue(device.signDocs.single().contains(""""memo":"a\u003cb\u003e\u0026\"c\\\n\t\b\f\u0001\u2028é""""))
    }

    @Test
    fun buildAminoSigned_depositWithoutMemo_omitsMemoField() {
        build(TxMessage.Deposit(Asset.Rune, BigInteger.ONE, "", address))

        assertTrue(device.signDocs.single().contains(""""value":{"coins":[{"amount":"1","asset":"THOR.RUNE"}],"signer":"$address"}"""))
    }

    @Test
    fun buildAminoSigned_txRaw_legacyAminoSignerInfoAndProtoMessages() {
        val message = TxMessage.Send(address, other, BigInteger.TEN, "rune")

        val tx = TxOuterClass.TxRaw.parseFrom(build(message, memo = "m"))

        val body = TxOuterClass.TxBody.parseFrom(tx.bodyBytes)
        val signerInfo = TxOuterClass.AuthInfo.parseFrom(tx.authInfoBytes).getSignerInfos(0)
        assertEquals(listOf(TxBuilder.msgSend(address, other, BigInteger.TEN, "rune")), body.messagesList)
        assertEquals("m", body.memo)
        assertEquals(SignMode.SIGN_MODE_LEGACY_AMINO_JSON, signerInfo.modeInfo.single.mode)
        assertEquals(456L, signerInfo.sequence)
        assertArrayEquals(device.publicKey, Keys.PubKey.parseFrom(signerInfo.publicKey.value).key.toByteArray())
        val digest = Utils.sha256(device.signDocs.single().encodeToByteArray())
        assertTrue(Secp256k1.get().verify(tx.getSignatures(0).toByteArray(), digest, device.publicKey))
    }

    @Test
    fun buildAminoSigned_signatureNotOfThisDoc_throwsInvalidSignature() {
        val wrongKey = FakeDevice(privateKey.add(BigInteger.ONE), publicKey = device.publicKey)

        assertThrows(SendError.InvalidSignature::class.java) {
            build(TxMessage.Send(address, other, BigInteger.ONE, "rune"), signer = wrongKey)
        }
    }

    @Test
    fun send_externalSigner_broadcastsAminoSignedTx() {
        val api = AccountApi()

        runBlocking { sender(api).send(other, BigInteger.ONE, "rune", null, device) }

        assertEquals(1, api.broadcastCalls)
        assertEquals(1, device.signDocs.size)
    }

    @Test
    fun deposit_externalSigner_signsMsgDepositDoc() {
        val api = AccountApi()

        runBlocking { sender(api).deposit(Asset.fromString("BTC-BTC"), BigInteger.ONE, "=:BTC.BTC:bc1q", device) }

        assertTrue(device.signDocs.single().contains(""""type":"thorchain/MsgDeposit""""))
        assertTrue(device.signDocs.single().contains(""""gas":"${TxBuilder.DEPOSIT_GAS_LIMIT}""""))
        assertEquals(1, api.broadcastCalls)
    }

    @Test
    fun signSend_externalSigner_returnsRawWithoutBroadcast() {
        val api = AccountApi()

        val signed = runBlocking { sender(api).signSend(other, BigInteger.ONE, "rune", "memo", device) }

        assertEquals(TxBuilder.txHash(signed.raw), signed.hash)
        assertEquals(12345L, signed.accountNumber)
        assertEquals(7L, signed.sequence)
        assertEquals(0, api.broadcastCalls)
    }

    @Test
    fun send_externalSignerOfAnotherKey_throwsSignerMismatch() {
        val stranger = FakeDevice(privateKey.add(BigInteger.ONE))

        assertThrows(SendError.SignerMismatch::class.java) {
            runBlocking { sender(AccountApi()).send(other, BigInteger.ONE, "rune", null, stranger) }
        }
        assertTrue(stranger.signDocs.isEmpty())
    }

    @Test
    fun externalSignerOverloads_maya_throwBeforeAnyRequest() {
        val mayaAddress = Address.fromPublicKey(device.publicKey, Network.MayaMainnet)
        val api = CountingApi()
        val maya = TransactionSender(mayaAddress, Network.MayaMainnet, ThornodeApiProvider(listOf(api)))
        val recipient = Address(mayaAddress.prefix, other.payload)
        val calls = listOf<suspend () -> Unit>(
            { maya.send(recipient, BigInteger.ONE, "cacao", null, device) },
            { maya.signSend(recipient, BigInteger.ONE, "cacao", null, device) },
            { maya.deposit(Asset.Rune, BigInteger.ONE, "memo", device) },
        )

        calls.forEach { call ->
            val error = assertThrows(TransactionSender.SendError.ExternalSignerUnsupportedNetwork::class.java) {
                runBlocking { call() }
            }
            assertEquals(Network.MayaMainnet, error.network)
        }
        assertEquals(0, api.requests)
        assertTrue(device.signDocs.isEmpty())
    }

    private fun build(
        message: TxMessage,
        memo: String = "",
        gasLimit: Long = 50_000_000,
        feeAmount: BigInteger? = null,
        signer: ThorchainExternalSigner = device,
    ): ByteArray = runBlocking {
        TxBuilder.buildAminoSigned(listOf(message), memo, 123, 456, "thorchain-1", gasLimit, feeAmount, "rune", signer)
    }

    private fun sender(api: FakeThornodeApi) =
        TransactionSender(address, Network.Mainnet, ThornodeApiProvider(listOf(api)))

    private fun com.google.gson.JsonObject.depositCoin() =
        getAsJsonArray("msgs")[0].asJsonObject.getAsJsonObject("value").getAsJsonArray("coins")[0].asJsonObject

    private class CountingApi : FakeThornodeApi() {
        var requests = 0

        override suspend fun account(address: String): AccountResponse {
            requests++
            throw NotImplementedError("account")
        }

        override suspend fun broadcast(request: BroadcastRequest): BroadcastResponse {
            requests++
            throw NotImplementedError("broadcast")
        }
    }

    private inner class AccountApi : FakeThornodeApi() {
        override suspend fun account(address: String): AccountResponse = AccountResponse(
            JsonParser.parseString(
                """{"@type":"/cosmos.auth.v1beta1.BaseAccount","address":"$address","account_number":"12345","sequence":"7"}"""
            ).asJsonObject
        )

        override suspend fun broadcast(request: BroadcastRequest): BroadcastResponse {
            broadcastCalls++
            val hash = TxBuilder.txHash(Base64.getDecoder().decode(request.txBytes))
            return BroadcastResponse(TxResponse("0", hash, null, 0, null))
        }
    }

    /** Signs SHA-256 of the doc like the THORChain app; [publicKey] may lie to model a wrong device. */
    private class FakeDevice(
        private val key: BigInteger,
        override val publicKey: ByteArray = Signer(key).publicKey,
    ) : ThorchainExternalSigner {
        val signDocs = mutableListOf<String>()

        override suspend fun signAmino(signDoc: ByteArray): ByteArray {
            signDocs += signDoc.decodeToString()
            return Signer(key).sign(Utils.sha256(signDoc))
        }
    }
}
