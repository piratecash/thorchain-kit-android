package io.horizontalsystems.thorchainkit

import io.horizontalsystems.thorchainkit.models.Address
import com.sun.net.httpserver.HttpServer
import io.horizontalsystems.thorchainkit.network.Network
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.net.InetSocketAddress
import java.net.URL
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.Semaphore

class DesktopThorchainKitTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val walletId = "desktop-test"
    private val databaseName = "thorchain-Mainnet-$walletId"
    private val address = Address.fromString("thor1gm00vwsfcp48enm4uv9e5dhm37jtd0ye27wrx0", Network.Mainnet)

    // Nothing listens there: the kit must not reach live endpoints from a unit test.
    private val offline = listOf(URL("http://127.0.0.1:1/"))

    private val databaseKey = ByteArray(32) { it.toByte() }

    private fun dataDir(): File = File(tmp.root, "data")

    private fun watchKit(dataDir: File): ThorchainKit =
        ThorchainKit.getInstance(dataDir, address, Network.Mainnet, walletId, databaseKey, thornodeUrls = offline, midgardUrls = offline)

    @Test
    fun getInstance_watchAddress_createsDatabaseInDataDir() {
        val dataDir = dataDir()

        val kit = watchKit(dataDir)

        assertTrue(kit.getTransactions().isEmpty())
        assertEquals(address.toString(), kit.receiveAddress)
        assertTrue(File(dataDir, databaseName).isFile)
    }

    @Test
    fun getInstance_prefixMismatch_throwsWithoutCreatingDatabase() {
        val dataDir = dataDir()

        val result = runCatching {
            ThorchainKit.getInstance(dataDir, address, Network.MayaMainnet, walletId, databaseKey)
        }

        assertTrue(result.exceptionOrNull() is IllegalArgumentException)
        assertTrue(dataDir.listFiles().orEmpty().isEmpty())
    }

    @Test
    fun clear_databaseOpenedByKit_removesFiles() {
        val dataDir = dataDir()
        val kit = watchKit(dataDir)
        kit.getTransactions()
        kit.start()
        kit.stop()

        ThorchainKit.clear(dataDir, Network.Mainnet, walletId)

        val left = dataDir.listFiles().orEmpty().filter { it.name.startsWith(databaseName) }
        assertFalse(left.map { it.name }.toString(), left.isNotEmpty())
    }

    @Test
    fun getInstance_eventListenerFactory_receivesCallEvents() {
        val server = thornodeStub()
        val base = "http://127.0.0.1:${server.address.port}"
        val factory = RecordingEventListenerFactory()
        val kit = ThorchainKit.getInstance(
            dataDir(), address, Network.Mainnet, walletId, databaseKey,
            syncInterval = 15,
            thornodeUrls = listOf(URL("$base/thornode/")),
            midgardUrls = listOf(URL("$base/midgard/")),
            eventListenerFactory = factory
        )

        try {
            kit.start()
            val deadline = System.currentTimeMillis() + 10_000
            while (factory.startedUrls.none { it.startsWith("$base/midgard/") } && System.currentTimeMillis() < deadline) {
                Thread.sleep(50)
            }
        } finally {
            kit.stop()
            server.stop(0)
        }

        assertTrue(factory.startedUrls.toString(), factory.startedUrls.any { it.startsWith("$base/thornode/") })
        assertTrue(factory.startedUrls.toString(), factory.startedUrls.any { it.startsWith("$base/midgard/") })
    }

    @Test
    fun stop_afterTransactionsSynced_restartReportsSyncedOnlyAfterNewHistoryFetch() {
        val midgardGate = Semaphore(OPEN_GATE)
        val server = thornodeStub(midgardGate)
        val base = "http://127.0.0.1:${server.address.port}"
        // a 1 s timer: a restart racing the previous round's teardown skips one round, not the test
        val kit = ThorchainKit.getInstance(
            dataDir(), address, Network.Mainnet, walletId, databaseKey,
            syncInterval = 1,
            thornodeUrls = listOf(URL("$base/thornode/")),
            midgardUrls = listOf(URL("$base/midgard/"))
        )

        try {
            kit.start()
            awaitTransactionsSynced(kit)

            kit.stop()
            assertFalse(kit.transactionsSyncState is ThorchainKit.SyncState.Synced)

            midgardGate.drainPermits()
            kit.start()
            kit.refresh()
            Thread.sleep(500)
            assertFalse(kit.transactionsSyncState is ThorchainKit.SyncState.Synced)

            midgardGate.release(OPEN_GATE)
            awaitTransactionsSynced(kit)
        } finally {
            kit.stop()
            midgardGate.release(OPEN_GATE)
            server.stop(0)
        }
    }

    @Test
    fun refresh_whileFeeLookupHangs_runsNextHistoryRoundWithoutSecondLookup() {
        val paths = ConcurrentLinkedQueue<String>()
        val feeLookupGate = CountDownLatch(1)
        val server = thornodeStub(paths = paths, actions = "[$WALLET_SEND_ACTION]", feeLookupGate = feeLookupGate)
        val base = "http://127.0.0.1:${server.address.port}"
        val kit = ThorchainKit.getInstance(
            dataDir(), address, Network.Mainnet, walletId, databaseKey,
            syncInterval = 600,
            thornodeUrls = listOf(URL("$base/thornode/")),
            midgardUrls = listOf(URL("$base/midgard/"))
        )

        try {
            kit.start()
            awaitRequests(paths, FEE_LOOKUP_PATH, 1)
            val historyRequests = paths.count { it.startsWith(MIDGARD_PATH) }

            kit.refresh()
            awaitRequests(paths, MIDGARD_PATH, historyRequests + 1)
            Thread.sleep(500)

            assertEquals(1, paths.count { it.startsWith(FEE_LOOKUP_PATH) })
        } finally {
            feeLookupGate.countDown()
            kit.stop()
            server.stop(0)
        }
    }

    @Test
    fun statusInfo_eachNetwork_labelsBalanceWithNativeCoin() {
        val mayaAddress = Address.fromString("maya1qc30hsy23lgf3hnkwypg3p4ecxw3yad94l2rm7", Network.MayaMainnet)
        val thorchain = watchKit(dataDir())
        val maya = ThorchainKit.getInstance(
            dataDir(), mayaAddress, Network.MayaMainnet, walletId, databaseKey, thornodeUrls = offline, midgardUrls = offline
        )

        assertTrue(thorchain.statusInfo().containsKey("RUNE Balance"))
        assertTrue(maya.statusInfo().containsKey("CACAO Balance"))
        assertFalse(maya.statusInfo().containsKey("RUNE Balance"))
    }

    private fun awaitTransactionsSynced(kit: ThorchainKit) = runBlocking {
        withTimeout(10_000) {
            kit.transactionsSyncStateFlow.first { it is ThorchainKit.SyncState.Synced }
        }
    }

    private fun awaitRequests(paths: Collection<String>, prefix: String, count: Int) {
        val deadline = System.currentTimeMillis() + 10_000
        while (paths.count { it.startsWith(prefix) } < count) {
            check(System.currentTimeMillis() < deadline) { "no request $count to $prefix: $paths" }
            Thread.sleep(20)
        }
    }

    // Answers just enough THORNode for the syncer to reach Midgard; Midgard waits for a gate permit,
    // a fee lookup for its own gate and then gets 404.
    private fun thornodeStub(
        midgardGate: Semaphore = Semaphore(OPEN_GATE),
        paths: MutableCollection<String> = ConcurrentLinkedQueue(),
        actions: String = "[]",
        feeLookupGate: CountDownLatch = CountDownLatch(0)
    ): HttpServer =
        HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
            executor = Executors.newCachedThreadPool()
            createContext("/") { exchange ->
                val path = exchange.requestURI.path
                paths.add(path)
                if (path.startsWith(MIDGARD_PATH)) midgardGate.acquireUninterruptibly()
                if (path.startsWith(FEE_LOOKUP_PATH)) feeLookupGate.await()
                val (code, body) = when {
                    path.startsWith(FEE_LOOKUP_PATH) -> 404 to "{}"
                    path.contains("/lastblock/") -> 200 to """[{"thorchain": 1}]"""
                    path.contains("/balances/") -> 200 to """{"balances": []}"""
                    else -> 200 to """{"actions": $actions, "meta": {}}"""
                }
                val bytes = body.toByteArray()
                exchange.sendResponseHeaders(code, bytes.size.toLong())
                exchange.responseBody.use { it.write(bytes) }
            }
            start()
        }

    private companion object {
        const val OPEN_GATE = 1_000
        const val MIDGARD_PATH = "/midgard/"
        const val FEE_LOOKUP_PATH = "/thornode/cosmos/tx/v1beta1/txs/"
        const val WALLET_SEND_ACTION = """
            {"type": "send", "status": "success", "date": "1784454197000000000", "height": "100",
             "in": [{"address": "thor1gm00vwsfcp48enm4uv9e5dhm37jtd0ye27wrx0", "txID": "AA",
                     "coins": [{"asset": "THOR.RUNE", "amount": "100"}]}],
             "out": [], "pools": []}
        """
    }
}
