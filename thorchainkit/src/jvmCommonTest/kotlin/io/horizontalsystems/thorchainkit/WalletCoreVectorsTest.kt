package io.horizontalsystems.thorchainkit

import cosmos.tx.v1beta1.TxOuterClass
import io.horizontalsystems.thorchainkit.crypto.Bech32
import io.horizontalsystems.thorchainkit.models.Address
import io.horizontalsystems.thorchainkit.network.Network
import io.horizontalsystems.thorchainkit.transaction.Signer
import io.horizontalsystems.thorchainkit.transaction.TxBuilder
import org.junit.Assert.assertEquals
import org.junit.Test
import java.math.BigInteger
import java.util.Base64

// vectors from trustwallet/wallet-core tests/chains/Cosmos/THORChain —
// the signed tx is real: mainnet 7E480FA163F6C6AFA17593F214C7BBC218F69AE3BC72366E39042AF381BFE105
class WalletCoreVectorsTest {

    @Test
    fun addressKeyHash() {
        val (hrp, payload) = Bech32.decode("thor1z53wwe7md6cewz9sqwqzn0aavpaun0gw0exn2r")

        assertEquals("thor", hrp)
        assertEquals("1522e767db6eb19708b0038029bfbd607bc9bd0e", payload.toHexString())
        assertEquals(
            "thor1z53wwe7md6cewz9sqwqzn0aavpaun0gw0exn2r",
            Address("thor", payload).toString()
        )
    }

    @Test
    fun privateKeyToAddress() {
        val privateKey = BigInteger("7105512f0c020a1dd759e14b865ec0125f59ac31e34d7a2807a228ed50cb343e", 16)

        assertEquals(
            "thor1z53wwe7md6cewz9sqwqzn0aavpaun0gw0exn2r",
            Signer.address(privateKey, Network.Mainnet).toString()
        )
    }

    @Test
    fun signedMsgSend_matchesConfirmedMainnetTx() {
        val privateKey = BigInteger("7105512f0c020a1dd759e14b865ec0125f59ac31e34d7a2807a228ed50cb343e", 16)
        val signer = Signer(privateKey)

        val from = Address.fromString("thor1z53wwe7md6cewz9sqwqzn0aavpaun0gw0exn2r", Network.Mainnet)
        val to = Address.fromString("thor1e2ryt8asq4gu0h6z2sx9u7rfrykgxwkmr9upxn", Network.Mainnet)

        val txRaw = TxBuilder.buildSigned(
            messages = listOf(TxBuilder.msgSend(from, to, BigInteger.valueOf(38_000_000), "rune")),
            memo = "",
            accountNumber = 593,
            sequence = 21,
            chainId = "thorchain-mainnet-v1",
            gasLimit = 2_500_000,
            feeAmount = BigInteger.valueOf(200),
            feeDenom = "rune",
            signer = signer
        )

        assertEquals(
            "ClIKUAoOL3R5cGVzLk1zZ1NlbmQSPgoUFSLnZ9tusZcIsAOAKb+9YHvJvQ4SFMqGRZ+wBVHH30JUDF54aRksgzrb" +
                    "GhAKBHJ1bmUSCDM4MDAwMDAwEmYKUApGCh8vY29zbW9zLmNyeXB0by5zZWNwMjU2azEuUHViS2V5EiMKIQPtmX45" +
                    "bPQpL1/OWkK7pBWZzNXZbjExVKfJ6nBJ3jF8dxIECgIIARgVEhIKCwoEcnVuZRIDMjAwEKDLmAEaQKZtS3ATa26O" +
                    "OGvqdKm14ZbHeNfkPtIajXi5MkZ5XaX2SWOeX+YnCPZ9TxF9Jj5cVIo71m55xq4hVL3yDbRe89g=",
            Base64.getEncoder().encodeToString(txRaw)
        )

        // signature is the last 64 bytes of TxRaw (single signer)
        val decoded = TxOuterClass.TxRaw.parseFrom(txRaw)
        assertEquals(
            "a66d4b70136b6e8e386bea74a9b5e196c778d7e43ed21a8d78b93246795da5f649639e5fe62708f67d4f117d263e5c548a3bd66e79c6ae2154bdf20db45ef3d8",
            decoded.getSignatures(0).toByteArray().toHexString()
        )
    }

    @Test
    fun memoRoundTrip() {
        val privateKey = BigInteger("7105512f0c020a1dd759e14b865ec0125f59ac31e34d7a2807a228ed50cb343e", 16)
        val signer = Signer(privateKey)
        val from = Signer.address(privateKey, Network.Mainnet)
        val memo = "=:ETH.ETH:0x1c7b17362df9a7cc4f4a733792d81ee5b3b40331:0/1/0:te:0"

        val txRaw = TxBuilder.buildSigned(
            messages = listOf(TxBuilder.msgSend(from, from, BigInteger.ONE, "rune")),
            memo = memo,
            accountNumber = 1,
            sequence = 0,
            chainId = "thorchain-1",
            gasLimit = TxBuilder.DEFAULT_GAS_LIMIT,
            feeDenom = "rune",
            signer = signer
        )

        val decodedBody = TxOuterClass.TxBody.parseFrom(TxOuterClass.TxRaw.parseFrom(txRaw).bodyBytes)
        assertEquals(memo, decodedBody.memo)
        assertEquals(TxBuilder.TYPE_URL_MSG_SEND, decodedBody.getMessages(0).typeUrl)
    }
}
