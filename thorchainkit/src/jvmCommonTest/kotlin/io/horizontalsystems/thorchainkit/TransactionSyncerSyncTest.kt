package io.horizontalsystems.thorchainkit

import io.horizontalsystems.thorchainkit.ThorchainKit.SyncState
import io.horizontalsystems.thorchainkit.database.TransactionSyncerStorage
import io.horizontalsystems.thorchainkit.models.Transaction
import io.horizontalsystems.thorchainkit.network.ActionCoin
import io.horizontalsystems.thorchainkit.network.ActionTx
import io.horizontalsystems.thorchainkit.network.ActionsResponse
import io.horizontalsystems.thorchainkit.network.MidgardAction
import io.horizontalsystems.thorchainkit.network.MidgardApi
import io.horizontalsystems.thorchainkit.network.MidgardProvider
import io.horizontalsystems.thorchainkit.sync.TransactionSyncer
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private const val WALLET = "thor1gm00vwsfcp48enm4uv9e5dhm37jtd0ye27wrx0"

private fun action(hash: String, timestamp: Long, status: String = "success") = MidgardAction(
    type = "send",
    status = status,
    date = timestamp * 1_000_000_000,
    height = 100,
    incoming = listOf(
        ActionTx(
            address = WALLET,
            txId = hash,
            coins = listOf(ActionCoin("THOR.RUNE", "100"))
        )
    ),
    outgoing = emptyList(),
    pools = emptyList(),
    metadata = null
)

private fun page(actions: List<MidgardAction>, nextPageToken: String?) =
    ActionsResponse(actions, ActionsResponse.Meta(nextPageToken))

// pages keyed by the incoming nextPageToken (null = first page); txid queries answered separately
private class FakeMidgardApi : MidgardApi {
    val pages = mutableMapOf<String?, ActionsResponse>()
    val byTxId = mutableMapOf<String, ActionsResponse>()
    var pageCalls = 0
    val txIdCalls = mutableListOf<String>()

    override suspend fun actions(address: String, limit: Int, nextPageToken: String?, txId: String?): ActionsResponse {
        if (txId != null) {
            txIdCalls.add(txId)
            return byTxId[txId] ?: page(emptyList(), null)
        }
        pageCalls++
        return pages[nextPageToken] ?: page(emptyList(), null)
    }
}

private class FakeSyncerStorage : TransactionSyncerStorage {
    val transactions = mutableMapOf<String, Transaction>()
    var syncTimestamp: Long? = null
    var backfillToken: String? = null

    override fun getTransactionSyncTimestamp(): Long? = syncTimestamp
    override fun saveTransactionSyncTimestamp(timestamp: Long) { syncTimestamp = timestamp }
    override fun getBackfillPageToken(): String? = backfillToken
    override fun saveBackfillPageToken(token: String?) { backfillToken = token }
    override fun getPendingTransactions(): List<Transaction> = transactions.values.filter { it.isPending }
    override fun saveTransactions(transactions: List<Transaction>) {
        transactions.forEach { this.transactions[it.hash] = it }
    }
}

class TransactionSyncerSyncTest {

    private fun syncer(api: FakeMidgardApi, storage: FakeSyncerStorage) =
        TransactionSyncer(WALLET, MidgardProvider(listOf(api)), storage)

    @Test
    fun sync_stopsPagingAtWatermark() {
        val api = FakeMidgardApi()
        val storage = FakeSyncerStorage()
        storage.syncTimestamp = 1_000L

        // page 1 contains a new tx and an already-synced confirmed tx; more pages exist
        api.pages[null] = page(listOf(action("AA", 2_000), action("BB", 1_000)), "t1")
        api.pages["t1"] = page(listOf(action("CC", 500)), null)

        runBlocking { syncer(api, storage).sync() }

        assertEquals(1, api.pageCalls) // stopped at the watermark, page 2 never fetched
        assertEquals(setOf("AA", "BB"), storage.transactions.keys)
        assertEquals(2_000L, storage.syncTimestamp)
        assertNull(storage.backfillToken)
    }

    @Test
    fun sync_shortHistory_completesWithoutBackfill() {
        val api = FakeMidgardApi()
        val storage = FakeSyncerStorage()

        api.pages[null] = page(listOf(action("AA", 2_000), action("BB", 1_000)), null)

        val syncer = syncer(api, storage)
        runBlocking { syncer.sync() }

        assertEquals(setOf("AA", "BB"), storage.transactions.keys)
        assertEquals(2_000L, storage.syncTimestamp)
        assertNull(storage.backfillToken)
        assertTrue(syncer.syncState is SyncState.Synced)
    }

    @Test
    fun sync_pageCapHit_savesBackfillTokenAndCompletesLater() {
        val api = FakeMidgardApi()
        val storage = FakeSyncerStorage()

        // 22 pages of history: recent pass caps at 20 pages, the remainder must be
        // backfilled instead of silently skipped (the old behavior lost pages 21+)
        val totalPages = 22
        for (i in 0 until totalPages) {
            val token = if (i == 0) null else "t$i"
            val next = if (i == totalPages - 1) null else "t${i + 1}"
            val ts = 100_000L - i * 1_000L // newest first
            api.pages[token] = page(listOf(action("H$i", ts)), next)
        }

        val syncer = syncer(api, storage)
        runBlocking { syncer.sync() }

        // recent pass: pages 0..19; backfill (same round): resumes at t20 and finishes
        assertEquals(totalPages, api.pageCalls)
        assertEquals(totalPages, storage.transactions.size)
        assertNull(storage.backfillToken) // backfill completed, token cleared
        assertEquals(100_000L, storage.syncTimestamp)

        // next round is cheap: page 1 reaches the watermark immediately
        runBlocking { syncer.sync() }
        assertEquals(totalPages + 1, api.pageCalls)
    }

    @Test
    fun sync_backfillToken_survivesWatermarkWrites() {
        val storage = FakeSyncerStorage()

        storage.saveBackfillPageToken("resume-here")
        storage.saveTransactionSyncTimestamp(123L)

        assertEquals("resume-here", storage.getBackfillPageToken())
        assertEquals(123L, storage.getTransactionSyncTimestamp())
    }

    @Test
    fun sync_emptyRecentPage_doesNotAbortPendingBackfill() {
        // a lagging Midgard mirror can legitimately return an empty first page — that
        // must not clear a pending backfill token (which would permanently lose the
        // un-backfilled history); backfill proceeds and completes in the same round
        val api = FakeMidgardApi()
        val storage = FakeSyncerStorage()
        storage.syncTimestamp = 10_000L
        storage.backfillToken = "resume-here"

        api.pages[null] = page(emptyList(), null)
        api.pages["resume-here"] = page(listOf(action("OLD", 1_000)), null)

        runBlocking { syncer(api, storage).sync() }

        assertTrue(storage.transactions.containsKey("OLD"))
        assertNull(storage.backfillToken) // cleared by backfill itself, after completing
    }

    @Test
    fun sync_pendingRefresh_normalizesHashCasing() {
        // Midgard may report the txID in different casing than what is stored; the
        // refreshed row must land under the STORED hash (primary key), not create a
        // duplicate row that leaves the original pending forever
        val api = FakeMidgardApi()
        val storage = FakeSyncerStorage()
        storage.syncTimestamp = 10_000L

        val storedHash = "abcdefhash"
        storage.saveTransactions(listOf(TransactionSyncer.fromMidgardAction(action(storedHash, 1_000, status = "pending"))!!))

        api.pages[null] = page(emptyList(), null)
        api.byTxId[storedHash] = page(listOf(action(storedHash.uppercase(), 1_000, status = "success")), null)

        runBlocking { syncer(api, storage).sync() }

        assertEquals(1, storage.transactions.size)
        assertEquals("success", storage.transactions[storedHash]?.status)
    }

    @Test
    fun sync_stalePendingTransaction_refreshedByTxId() {
        val api = FakeMidgardApi()
        val storage = FakeSyncerStorage()
        storage.syncTimestamp = 10_000L

        // an old pending tx sits in the DB, no longer visible in recent pages
        val pendingHash = "PENDINGHASH"
        storage.saveTransactions(listOf(TransactionSyncer.fromMidgardAction(action(pendingHash, 1_000, status = "pending"))!!))

        // recent page has unrelated newer history
        api.pages[null] = page(listOf(action("AA", 11_000)), null)
        // txid lookup shows the pending tx has since confirmed
        api.byTxId[pendingHash] = page(listOf(action(pendingHash, 1_000, status = "success")), null)

        runBlocking { syncer(api, storage).sync() }

        assertEquals(listOf(pendingHash), api.txIdCalls)
        assertEquals("success", storage.transactions[pendingHash]?.status)
        assertTrue(storage.getPendingTransactions().isEmpty())
    }

    @Test
    fun sync_pendingSeenInRecentPages_notRefetchedByTxId() {
        val api = FakeMidgardApi()
        val storage = FakeSyncerStorage()

        val hash = "STILLPENDING"
        storage.saveTransactions(listOf(TransactionSyncer.fromMidgardAction(action(hash, 1_000, status = "pending"))!!))

        // the same pending tx is present in the recent page — no txid query needed
        api.pages[null] = page(listOf(action(hash, 1_000, status = "pending")), null)

        runBlocking { syncer(api, storage).sync() }

        assertTrue(api.txIdCalls.isEmpty())
        assertEquals("pending", storage.transactions[hash]?.status)
    }
}
