package io.horizontalsystems.thorchainkit

import io.horizontalsystems.thorchainkit.models.Address
import io.horizontalsystems.thorchainkit.models.Asset
import io.horizontalsystems.thorchainkit.transaction.Signer
import io.horizontalsystems.thorchainkit.transaction.TxBuilder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import types.MsgDepositOuterClass.MsgDeposit
import java.math.BigInteger

class TxBuilderTest {

    // golden vectors independently produced with manual protobuf wire encoding
    // and pure-python RFC6979 signing (no shared code with this implementation)
    private val privateKey = BigInteger("cd48c8b23a5d619cb67b7a4886d25127acf2e8c023e42a1e9ae14c6194532aa9", 16)
    private val signer = Signer(privateKey)
    private val from = Address("thor", "46def63a09c06a7ccf75e30b9a36fb8fa4b6bc99".hexToBytes())
    private val to = Address("thor", ByteArray(20) { it.toByte() })

    private val chainId = "thorchain-1"
    private val accountNumber = 12345L
    private val sequence = 7L
    private val gasLimit = 6_000_000L

    @Test
    fun buildSigned_msgSend() {
        val message = TxBuilder.msgSend(from, to, BigInteger.valueOf(1_000_000), "rune")
        val txRaw = TxBuilder.buildSigned(listOf(message), "test", accountNumber, sequence, chainId, gasLimit, feeDenom = "rune", signer = signer)

        assertEquals(
            "0a570a4f0a0e2f74797065732e4d736753656e64123d0a1446def63a09c06a7ccf75e30b9a36fb8fa4b6bc99" +
                    "1214000102030405060708090a0b0c0d0e0f101112131a0f0a0472756e651207313030303030301204746573" +
                    "7412590a500a460a1f2f636f736d6f732e63727970746f2e736563703235366b312e5075624b657912230a21" +
                    "02205c476a22d5fe10b74489db9479d0e36e25a32da393a771fcf12380136a451f12040a0208011807120510" +
                    "809bee021a40c6ef36b4764ef03c2de4720c2c0c44e62ff9fd57965ecd7d41de6e3d2e9a67e964bbc17ff703" +
                    "a2f67eb91b71e9f1ae6b5417ff9f5735a68ad7be8ba8687fb637",
            txRaw.toHexString()
        )
    }

    @Test
    fun buildSigned_msgDeposit() {
        val memo = "=:ETH.ETH:0x1c7b17362df9a7cc4f4a733792d81ee5b3b40331"
        val message = TxBuilder.msgDeposit(Asset.Rune, BigInteger.valueOf(1_000_000), memo, from)
        val txRaw = TxBuilder.buildSigned(listOf(message), "", accountNumber, sequence, chainId, gasLimit, feeDenom = "rune", signer = signer)

        assertEquals(
            "0a83010a80010a112f74797065732e4d73674465706f736974126b0a1d0a120a0454484f52120452554e451a" +
                    "0452554e4512073130303030303012343d3a4554482e4554483a307831633762313733363264663961376363" +
                    "3466346137333337393264383165653562336234303333311a1446def63a09c06a7ccf75e30b9a36fb8fa4b6" +
                    "bc9912590a500a460a1f2f636f736d6f732e63727970746f2e736563703235366b312e5075624b657912230a" +
                    "2102205c476a22d5fe10b74489db9479d0e36e25a32da393a771fcf12380136a451f12040a02080118071205" +
                    "10809bee021a4022918e6e8be2ae51c194db15adc36ca883c5037c1bbbd5a9464fdd5c3989add230c2fb5e1f" +
                    "43beda5d869c5fdc807aa3a4416f0bb7b9dd5d7f8f6d6be897a1ae",
            txRaw.toHexString()
        )
    }

    // Secured-asset swaps deposit the secured coin via MsgDeposit; the coin's `secured`
    // flag is what tells the node this is a secured asset rather than a native/synth. Decode
    // the built message and assert the flag (and asset fields) round-trip. No golden signing
    // vector needed — this targets serialization, not the signature.
    @Test
    fun msgDeposit_securedAsset_setsSecuredFlag() {
        val memo = "=:ETH.ETH:0x1c7b17362df9a7cc4f4a733792d81ee5b3b40331"
        val any = TxBuilder.msgDeposit(Asset.fromString("BTC-BTC"), BigInteger.valueOf(1_000_000), memo, from)

        assertEquals(TxBuilder.TYPE_URL_MSG_DEPOSIT, any.typeUrl)

        val message = MsgDeposit.parseFrom(any.value)
        assertEquals(memo, message.memo)
        assertEquals(1, message.coinsCount)

        val coin = message.getCoins(0)
        assertEquals("1000000", coin.amount)
        // amount is already in 1e8 base units; the coin's own decimals field stays 0 (as for
        // RUNE). If a secured deposit is ever found to need 8, update this and TxBuilder together.
        assertEquals(0L, coin.decimals)

        val asset = coin.asset
        assertEquals("BTC", asset.chain)
        assertEquals("BTC", asset.symbol)
        assertEquals("BTC", asset.ticker)
        assertTrue(asset.secured)
        assertFalse(asset.synth)
        assertFalse(asset.trade)
    }

    // A secured L1 token keeps the contract address in its symbol while the ticker is the bare
    // token — verify both survive the MsgDeposit serialization.
    @Test
    fun msgDeposit_securedTokenAsset_keepsContractSymbol() {
        val any = TxBuilder.msgDeposit(
            Asset.fromString("ETH-USDC-0xa0b86991c6218b36c1d19d4a2e9eb0ce3606eb48"),
            BigInteger.valueOf(1_000_000),
            "=:THOR.RUNE:thor1g6pnmnyeg48yc3lg796plt0uw50qpp7humfggz",
            from
        )

        val asset = MsgDeposit.parseFrom(any.value).getCoins(0).asset
        assertEquals("ETH", asset.chain)
        assertEquals("USDC-0XA0B86991C6218B36C1D19D4A2E9EB0CE3606EB48", asset.symbol)
        assertEquals("USDC", asset.ticker)
        assertTrue(asset.secured)
    }

    @Test
    fun assetNotation() {
        assertEquals(Asset("THOR", "RUNE", "RUNE"), Asset.fromString("THOR.RUNE"))
        assertEquals(Asset("BTC", "BTC", "BTC", secured = true), Asset.fromString("BTC-BTC"))
        assertEquals(Asset("BTC", "BTC", "BTC", synth = true), Asset.fromString("BTC/BTC"))
        assertEquals(Asset("BTC", "BTC", "BTC", trade = true), Asset.fromString("BTC~BTC"))
        assertEquals(
            Asset("ETH", "USDC-0XA0B86991C6218B36C1D19D4A2E9EB0CE3606EB48", "USDC"),
            Asset.fromString("ETH.USDC-0xa0b86991c6218b36c1d19d4a2e9eb0ce3606eb48")
        )
        assertEquals("THOR.RUNE", Asset.Rune.toString())
        assertEquals("BTC-BTC", Asset("BTC", "BTC", "BTC", secured = true).toString())
    }
}
