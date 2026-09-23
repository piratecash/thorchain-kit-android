package io.horizontalsystems.thorchainkit

import io.horizontalsystems.thorchainkit.models.Address
import io.horizontalsystems.thorchainkit.network.Network
import io.horizontalsystems.thorchainkit.network.ThornodeApiProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Ignore
import org.junit.Test

// live-API smoke tests against mayanode; run manually (remove @Ignore) when network is available
@Ignore("hits live Maya mainnet endpoints")
class MayaApiIntegrationTest {

    private val network = Network.MayaMainnet
    private val thornode = ThornodeApiProvider.create(network.thornodeUrls, network.protocolPath)

    // reserve module account — always exists and holds CACAO. Same 20-byte payload as the
    // THORChain reserve (cosmos-sdk derives module accounts from the module name, HRP-independent),
    // re-encoded with the "maya" prefix.
    private val reserve = Address.fromString("maya1dheycdevq39qlkxs2a6wuuzyn4aqxhve4hc8sm", network)

    @Test
    fun fetchBalances() = runBlocking {
        val balances = thornode.fetchBalances(reserve)

        assertTrue(balances.any { it.denom == "cacao" && it.amount.signum() > 0 })
    }

    @Test
    fun fetchChainId() = runBlocking {
        assertEquals("mayachain-mainnet-v1", thornode.fetchChainId())
    }

    // fee is read from mayachain/constants → int_64_values.NativeTransactionFee (in cacao base
    // units), the same key THORChain uses — so fetchNativeTxFee works cross-chain.
    @Test
    fun fetchNativeTxFee() = runBlocking {
        assertTrue(thornode.fetchNativeTxFee().signum() > 0)
    }

    // /mayachain/lastblock/mayachain returns the height under a `mayachain` key (not `thorchain`);
    // fetchLastBlockHeight reads it protocol-driven via protocolPath.
    @Test
    fun fetchLastBlockHeight() = runBlocking {
        assertTrue(thornode.fetchLastBlockHeight() > 17_000_000L)
    }
}
