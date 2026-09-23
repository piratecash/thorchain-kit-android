package io.horizontalsystems.thorchainkit

import io.horizontalsystems.hdwalletkit.Mnemonic
import io.horizontalsystems.thorchainkit.network.MidgardProvider
import io.horizontalsystems.thorchainkit.network.Network
import io.horizontalsystems.thorchainkit.network.ThornodeApiProvider
import io.horizontalsystems.thorchainkit.transaction.Signer
import io.horizontalsystems.thorchainkit.transaction.TransactionSender
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.math.BigInteger

// Broadcasts a REAL transaction (send-to-self, 0.00000001 RUNE + network fee ~0.02 RUNE).
// Skipped automatically unless a funded mnemonic is supplied via env var, so the normal
// suite stays hermetic. To run:
//
//   THORCHAIN_TEST_MNEMONIC="word word ..." ./gradlew :thorchainkit:testDebugUnitTest \
//       --tests "*SendIntegrationTest*"
//
//   THORCHAIN_TEST_NETWORK   - Mainnet (default) or Stagenet
//
// Note: public stagenet endpoints are currently in transition; for stagenet also
// override Network.Stagenet URLs or use a self-hosted node.
class SendIntegrationTest {

    @Test
    fun sendToSelf_confirmAndVerifyViaMidgard() = runBlocking {
        val mnemonic = System.getenv("THORCHAIN_TEST_MNEMONIC")
        assumeTrue("THORCHAIN_TEST_MNEMONIC not set; skipping real-broadcast test", mnemonic != null)

        val network = System.getenv("THORCHAIN_TEST_NETWORK")
            ?.let { Network.valueOf(it) } ?: Network.Mainnet

        val seed = Mnemonic().toSeed(mnemonic.trim().split(" "))
        val signer = Signer.getInstance(seed, network)
        val privateKey = Signer.privateKey(seed, network)
        val address = Signer.address(privateKey, network)

        val thornode = ThornodeApiProvider.create(network.thornodeUrls)
        val midgard = MidgardProvider.create(network.midgardUrls)

        val fee = thornode.fetchNativeTxFee()
        val balance = thornode.fetchBalances(address).firstOrNull { it.denom == "rune" }?.amount ?: BigInteger.ZERO
        println("address: $address, rune balance: $balance, network fee: $fee")
        assertTrue("insufficient balance to cover the network fee", balance > fee)

        val sender = TransactionSender(address, network, thornode)
        val txHash = sender.send(
            to = address,
            amount = BigInteger.ONE,
            denom = "rune",
            memo = "thorchain-kit-android integration test",
            signer = signer
        )
        println("broadcast accepted, hash: $txHash")

        // ~6s blocks; wait for inclusion
        var confirmed = false
        for (attempt in 0 until 30) {
            delay(3000)
            val tx = thornode.fetchTransaction(txHash)
            if (tx != null) {
                assertEquals("tx failed on-chain: ${tx.rawLog}", 0, tx.code ?: -1)
                println("confirmed in block ${tx.height}")
                confirmed = true
                break
            }
        }
        assertTrue("transaction not confirmed in time", confirmed)

        // Midgard indexes with a small delay
        var indexed = false
        for (attempt in 0 until 20) {
            delay(3000)
            val (actions, _) = midgard.fetchActions(address.toString(), limit = 10)
            if (actions.any { action ->
                    (action.incoming ?: emptyList()).any { it.txId.equals(txHash, ignoreCase = true) }
                }) {
                indexed = true
                break
            }
        }
        assertTrue("transaction not indexed by midgard in time", indexed)
    }
}
