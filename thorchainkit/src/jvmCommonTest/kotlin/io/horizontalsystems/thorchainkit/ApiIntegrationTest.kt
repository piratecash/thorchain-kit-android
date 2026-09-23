package io.horizontalsystems.thorchainkit

import io.horizontalsystems.thorchainkit.models.Address
import io.horizontalsystems.thorchainkit.network.MidgardProvider
import io.horizontalsystems.thorchainkit.network.Network
import io.horizontalsystems.thorchainkit.network.ThornodeApiProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Ignore
import org.junit.Test

// live-API smoke tests; run manually (remove @Ignore) when network is available
@Ignore("hits live mainnet endpoints")
class ApiIntegrationTest {

    private val thornode = ThornodeApiProvider.create(Network.Mainnet.thornodeUrls)
    private val midgard = MidgardProvider.create(Network.Mainnet.midgardUrls)

    // reserve module account — always exists and holds RUNE
    private val reserve = Address.fromString("thor1dheycdevq39qlkxs2a6wuuzyn4aqxhve4qxtxt", Network.Mainnet)

    @Test
    fun fetchBalances() = runBlocking {
        val balances = thornode.fetchBalances(reserve)

        assertTrue(balances.any { it.denom == "rune" && it.amount.signum() > 0 })
    }

    @Test
    fun fetchAccount() = runBlocking {
        val accountInfo = thornode.fetchAccount(reserve)

        assertEquals(8L, accountInfo?.accountNumber)
    }

    @Test
    fun fetchNativeTxFee() = runBlocking {
        val fee = thornode.fetchNativeTxFee()

        assertTrue(fee.signum() > 0)
    }

    @Test
    fun fetchLastBlockHeight() = runBlocking {
        assertTrue(thornode.fetchLastBlockHeight() > 27_000_000L)
    }

    @Test
    fun fetchChainId() = runBlocking {
        assertEquals("thorchain-1", thornode.fetchChainId())
    }

    @Test
    fun fetchTransaction_notFound() = runBlocking {
        assertEquals(null, thornode.fetchTransaction("0000000000000000000000000000000000000000000000000000000000000000"))
    }

    @Test
    fun fetchActions() = runBlocking {
        val (actions, nextPageToken) = midgard.fetchActions(reserve.toString(), limit = 5)

        assertTrue(actions.isNotEmpty())
        assertTrue(nextPageToken != null)
    }
}
