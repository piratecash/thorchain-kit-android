package io.horizontalsystems.thorchainkit

import com.google.gson.Gson
import io.horizontalsystems.thorchainkit.ThorchainKit.SyncState
import io.horizontalsystems.thorchainkit.database.TransactionSyncerStorage
import io.horizontalsystems.thorchainkit.models.Transaction
import io.horizontalsystems.thorchainkit.network.ActionCoin
import io.horizontalsystems.thorchainkit.network.ActionTx
import io.horizontalsystems.thorchainkit.network.ActionsResponse
import io.horizontalsystems.thorchainkit.network.MidgardAction
import io.horizontalsystems.thorchainkit.network.MidgardApi
import io.horizontalsystems.thorchainkit.network.MidgardProvider
import io.horizontalsystems.thorchainkit.network.Network
import io.horizontalsystems.thorchainkit.network.ThornodeApiProvider
import io.horizontalsystems.thorchainkit.network.TxDetailsResponse
import io.horizontalsystems.thorchainkit.sync.TransactionSyncer
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigInteger
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

private const val WALLET = "thor1gm00vwsfcp48enm4uv9e5dhm37jtd0ye27wrx0"

private fun action(hash: String, timestamp: Long, status: String = "success", address: String = WALLET) = MidgardAction(
    type = "send",
    status = status,
    date = timestamp * 1_000_000_000,
    height = 100,
    incoming = listOf(
        ActionTx(
            address = address,
            txId = hash,
            coins = listOf(ActionCoin("THOR.RUNE", "100"))
        )
    ),
    outgoing = emptyList(),
    pools = emptyList(),
    metadata = null
)

private fun midgardAction(json: String): MidgardAction = Gson().fromJson(json, MidgardAction::class.java)

private fun page(actions: List<MidgardAction>, nextPageToken: String?) =
    ActionsResponse(actions, ActionsResponse.Meta(nextPageToken))

// pages keyed by the incoming nextPageToken (null = first page); txid queries answered separately
private class FakeMidgardApi : MidgardApi {
    val pages = mutableMapOf<String?, ActionsResponse>()
    val byTxId = mutableMapOf<String, ActionsResponse>()
    var pageCalls = 0
    val txIdCalls = mutableListOf<String>()
    var pageGate: CompletableDeferred<Unit>? = null

    override suspend fun actions(address: String, limit: Int, nextPageToken: String?, txId: String?): ActionsResponse {
        if (txId != null) {
            txIdCalls.add(txId)
            return byTxId[txId] ?: page(emptyList(), null)
        }
        pageCalls++
        pageGate?.await()
        return pages[nextPageToken] ?: page(emptyList(), null)
    }
}

// answers each hash with its fixture, 404 for an unknown hash, or the HTTP error set for it
private class FakeNodeApi : FakeThornodeApi() {
    val responses = mutableMapOf<String, String>()
    val errors = mutableMapOf<String, Int>()
    val calls = CopyOnWriteArrayList<String>()
    val gates = mutableMapOf<String, CompletableDeferred<Unit>>()

    override suspend fun transactionDetails(hash: String): TxDetailsResponse {
        calls.add(hash)
        gates[hash]?.await()
        errors[hash]?.let { throw httpException(it) }
        val json = responses[hash] ?: throw httpException(404)
        return Gson().fromJson(json, TxDetailsResponse::class.java)
    }
}

private class FakeSyncerStorage : TransactionSyncerStorage {
    val transactions = ConcurrentHashMap<String, Transaction>()
    val resolvedFeeLookups = ConcurrentHashMap<String, Boolean>()
    // holds saveTransactions between reading the stored fees and writing the rows
    var saveEntered: CountDownLatch? = null
    var saveGate: CountDownLatch? = null
    var syncTimestamp: Long? = null
    var backfillToken: String? = null

    override fun getTransactionSyncTimestamp(): Long? = syncTimestamp
    override fun saveTransactionSyncTimestamp(timestamp: Long) { syncTimestamp = timestamp }
    override fun getBackfillPageToken(): String? = backfillToken
    override fun saveBackfillPageToken(token: String?) { backfillToken = token }
    override fun getPendingTransactions(): List<Transaction> = transactions.values.filter { it.isPending }
    override fun saveTransactions(transactions: List<Transaction>): List<Transaction> {
        val stored = transactions.map { it.copy(fee = it.fee ?: this.transactions[it.hash]?.fee) }
        saveEntered?.countDown()
        saveGate?.await()
        stored.forEach { this.transactions[it.hash] = it }
        return stored
    }

    override fun addFeeLookups(hashes: List<String>) {
        hashes.forEach { resolvedFeeLookups.putIfAbsent(it, false) }
    }

    override fun getFeeLookups(limit: Int): List<Transaction> =
        resolvedFeeLookups.filterValues { !it }.keys
            .mapNotNull { transactions[it] }
            .sortedByDescending { it.timestamp }
            .take(limit)

    override fun saveFee(hash: String, fee: BigInteger?): Transaction? {
        if (fee != null) transactions[hash]?.let { transactions[hash] = it.copy(fee = fee) }
        resolvedFeeLookups[hash] = true
        return transactions[hash]
    }
}

class TransactionSyncerSyncTest {

    private fun syncer(
        api: FakeMidgardApi,
        storage: FakeSyncerStorage,
        node: FakeNodeApi = FakeNodeApi(),
        wallet: String = WALLET,
        fallbackNode: FakeNodeApi? = null
    ) = TransactionSyncer(
        wallet, MidgardProvider(listOf(api)), ThornodeApiProvider(listOfNotNull(node, fallbackNode)),
        Network.Mainnet.assetResolver, storage
    )

    // one sync pass as the kit runs it: history first, then the fee lookups
    private fun syncWithFees(syncer: TransactionSyncer) = runBlocking {
        syncer.sync()
        syncer.syncFees()
    }

    private fun feeOf(actionJson: String, wallet: String, hash: String, nodeTxJson: String?): BigInteger? {
        val api = FakeMidgardApi()
        val storage = FakeSyncerStorage()
        val node = FakeNodeApi()
        api.pages[null] = page(listOf(midgardAction(actionJson)), null)
        nodeTxJson?.let { node.responses[hash] = it }

        syncWithFees(syncer(api, storage, node, wallet))

        return storage.transactions.getValue(hash).fee
    }

    @Test
    fun syncFees_walletSend_storesFeeFromNodeNotFromMidgard() {
        val fee = feeOf(
            MainnetFixtures.THOR_SEND_ACTION, "thor1t60f02r8jvzjrhtnjgfj4ne6rs5wjnejwmj7fh",
            "E0C97FCAB81C8CF22B235F38A7CAA97134719BD36C26746DA900B1DC7424E460", MainnetFixtures.THOR_SEND
        )

        assertEquals(BigInteger("2000000"), fee)
    }

    @Test
    fun syncFees_walletSwapDeposit_storesNativeFee() {
        val fee = feeOf(
            MainnetFixtures.THOR_SWAP_ACTION, "thor17hwqt302e5f2xm4h95ma8wuggqkvfzgvsnh5z9",
            "59444566178C0DFDE426F95E2E5AFDF9BB05D5CF5662BA1D70ED1D2428ECC3D1", MainnetFixtures.THOR_SWAP_DEPOSIT
        )

        assertEquals(BigInteger("2000000"), fee)
    }

    @Test
    fun syncFees_swapUnknownToNode_leavesFeeNullNotTheOutboundFee() {
        val fee = feeOf(
            MainnetFixtures.THOR_SWAP_WITH_OUTBOUND_FEE_ACTION, "thor1dl7un46w7l7f3ewrnrm6nq58nerjtp0dradjtd",
            "5BF80D0BCF354B0DAF35FDA033F7984F5D3CBACD7961ED7235C9D0D8B7FC6BFB", nodeTxJson = null
        )

        assertNull(fee)
    }

    @Test
    fun syncFees_walletRefund_storesFeeOfRefundedDeposit() {
        val fee = feeOf(
            MainnetFixtures.THOR_REFUND_ACTION, "thor166n4w5039meulfa3p6ydg60ve6ueac7tlt0jws",
            "D18DD519FCBFC4FE6E2D3C4D37ED269387172C3EC276B05731A5D94AB9179D55", MainnetFixtures.THOR_REFUNDED_DEPOSIT
        )

        assertEquals(BigInteger("2000000"), fee)
    }

    @Test
    fun syncFees_walletFailedAction_storesChargedFee() {
        val fee = feeOf(
            MainnetFixtures.THOR_FAILED_ACTION, "thor1z3e8pxs5fpgcdjpnn92y7xfv90enqm46qtxdl2",
            "A9AFEA02641C049E08CC310E626290EC7B22B83EB796AA36F0B202181C321C8E", MainnetFixtures.THOR_FAILED_DEPOSIT
        )

        assertEquals(BigInteger("2000000"), fee)
    }

    @Test
    fun syncFees_incomingTransaction_neverAsksNodeAndHasNoFee() {
        val api = FakeMidgardApi()
        val storage = FakeSyncerStorage()
        val node = FakeNodeApi()
        val hash = "E0C97FCAB81C8CF22B235F38A7CAA97134719BD36C26746DA900B1DC7424E460"
        api.pages[null] = page(listOf(midgardAction(MainnetFixtures.THOR_SEND_ACTION)), null)
        node.responses[hash] = MainnetFixtures.THOR_SEND

        syncWithFees(syncer(api, storage, node, wallet = "thor166n4w5039meulfa3p6ydg60ve6ueac7tlt0jws"))

        assertTrue(node.calls.isEmpty())
        assertNull(storage.transactions.getValue(hash).fee)
    }

    @Test
    fun syncFees_manyWalletTransactions_askNodeForTenNewestPerPass() {
        val api = FakeMidgardApi()
        val storage = FakeSyncerStorage()
        val node = FakeNodeApi()
        api.pages[null] = page((1..12).map { action("H$it", 1_000L + it) }, null)
        val syncer = syncer(api, storage, node)

        syncWithFees(syncer)
        assertEquals((12 downTo 3).map { "H$it" }, node.calls)

        syncWithFees(syncer)
        assertEquals(listOf("H2", "H1"), node.calls.drop(10))
    }

    @Test
    fun syncFees_resolvedLookup_neverAskedAgainAndFeeSurvivesResync() {
        val api = FakeMidgardApi()
        val storage = FakeSyncerStorage()
        val node = FakeNodeApi()
        val hash = "E0C97FCAB81C8CF22B235F38A7CAA97134719BD36C26746DA900B1DC7424E460"
        api.pages[null] = page(listOf(midgardAction(MainnetFixtures.THOR_SEND_ACTION)), null)
        node.responses[hash] = MainnetFixtures.THOR_SEND
        val syncer = syncer(api, storage, node, wallet = "thor1t60f02r8jvzjrhtnjgfj4ne6rs5wjnejwmj7fh")

        syncWithFees(syncer)
        syncWithFees(syncer)

        assertEquals(listOf(hash), node.calls)
        assertEquals(BigInteger("2000000"), storage.transactions.getValue(hash).fee)
    }

    @Test
    fun syncFees_rateLimited_leavesFeeNullKeepsSyncedStopsAndRetriesLater() {
        val api = FakeMidgardApi()
        val storage = FakeSyncerStorage()
        val node = FakeNodeApi()
        api.pages[null] = page(listOf(action("NEW", 2_000), action("OLD", 1_000)), null)
        node.errors["NEW"] = 429
        val syncer = syncer(api, storage, node)

        syncWithFees(syncer)

        assertEquals(listOf("NEW"), node.calls)
        assertNull(storage.transactions.getValue("NEW").fee)
        assertTrue(syncer.syncState is SyncState.Synced)

        node.errors.clear()
        syncWithFees(syncer)

        assertEquals(listOf("NEW", "NEW", "OLD"), node.calls)
    }

    @Test
    fun syncFees_nodeUnreachable_leavesFeeNullAndSyncSucceeds() {
        val api = FakeMidgardApi()
        val storage = FakeSyncerStorage()
        val node = FakeNodeApi()
        api.pages[null] = page(listOf(action("AA", 2_000)), null)
        node.errors["AA"] = 503
        val syncer = syncer(api, storage, node)

        syncWithFees(syncer)
        syncWithFees(syncer)

        assertEquals(listOf("AA", "AA"), node.calls)
        assertNull(storage.transactions.getValue("AA").fee)
        assertTrue(syncer.syncState is SyncState.Synced)
    }

    @Test
    fun syncFees_feeFound_emitsTransactionWithFee() = runBlocking {
        val api = FakeMidgardApi()
        val storage = FakeSyncerStorage()
        val node = FakeNodeApi()
        val hash = "E0C97FCAB81C8CF22B235F38A7CAA97134719BD36C26746DA900B1DC7424E460"
        api.pages[null] = page(listOf(midgardAction(MainnetFixtures.THOR_SEND_ACTION)), null)
        node.responses[hash] = MainnetFixtures.THOR_SEND
        val syncer = syncer(api, storage, node, wallet = "thor1t60f02r8jvzjrhtnjgfj4ne6rs5wjnejwmj7fh")
        val emitted = async(start = CoroutineStart.UNDISPATCHED) {
            syncer.transactionsFlow.first { transactions -> transactions.any { it.fee != null } }
        }

        syncer.sync()
        syncer.syncFees()

        assertEquals(BigInteger("2000000"), withTimeout(5_000) { emitted.await() }.single().fee)
    }

    @Test
    fun syncFees_malformedNodeResponse_leavesLookupOpenAndStopsPass() {
        val api = FakeMidgardApi()
        val storage = FakeSyncerStorage()
        val node = FakeNodeApi()
        api.pages[null] = page(listOf(action("NEW", 2_000), action("OLD", 1_000)), null)
        node.responses["NEW"] = MainnetFixtures.THOR_SEND.replace("\"code\":0,", "")
        node.responses["OLD"] = MainnetFixtures.THOR_SEND

        syncWithFees(syncer(api, storage, node, wallet = WALLET))

        assertEquals(listOf("NEW"), node.calls)
        assertEquals(false, storage.resolvedFeeLookups["NEW"])
        assertNull(storage.transactions.getValue("NEW").fee)
    }

    @Test
    fun syncFees_primaryNodeFails_asksNoOtherNodeInThatPass() {
        val api = FakeMidgardApi()
        val storage = FakeSyncerStorage()
        val primary = FakeNodeApi()
        val fallback = FakeNodeApi()
        api.pages[null] = page(listOf(action("NEW", 2_000), action("OLD", 1_000)), null)
        primary.errors["NEW"] = 503

        syncWithFees(syncer(api, storage, primary, fallbackNode = fallback))

        assertEquals(listOf("NEW"), primary.calls)
        assertTrue(fallback.calls.isEmpty())
    }

    @Test
    fun sync_afterFeeResolved_emitsStoredFee() = runBlocking {
        val api = FakeMidgardApi()
        val storage = FakeSyncerStorage()
        val node = FakeNodeApi()
        val hash = "E0C97FCAB81C8CF22B235F38A7CAA97134719BD36C26746DA900B1DC7424E460"
        api.pages[null] = page(listOf(midgardAction(MainnetFixtures.THOR_SEND_ACTION)), null)
        node.responses[hash] = MainnetFixtures.THOR_SEND
        val syncer = syncer(api, storage, node, wallet = "thor1t60f02r8jvzjrhtnjgfj4ne6rs5wjnejwmj7fh")
        syncer.sync()
        syncer.syncFees()
        val emitted = async(start = CoroutineStart.UNDISPATCHED) { syncer.transactionsFlow.first() }

        syncer.sync()

        assertEquals(BigInteger("2000000"), withTimeout(5_000) { emitted.await() }.single().fee)
    }

    @Test
    fun syncFees_feeSavedDuringHistorySave_feeSurvivesTheSave() = runBlocking {
        val api = FakeMidgardApi()
        val storage = FakeSyncerStorage()
        val node = FakeNodeApi()
        val hash = "E0C97FCAB81C8CF22B235F38A7CAA97134719BD36C26746DA900B1DC7424E460"
        api.pages[null] = page(listOf(midgardAction(MainnetFixtures.THOR_SEND_ACTION)), null)
        node.responses[hash] = MainnetFixtures.THOR_SEND
        val syncer = syncer(api, storage, node, wallet = "thor1t60f02r8jvzjrhtnjgfj4ne6rs5wjnejwmj7fh")
        syncer.sync()
        val nodeGate = CompletableDeferred<Unit>()
        node.gates[hash] = nodeGate
        val fees = launch(Dispatchers.IO) { syncer.syncFees() }
        val saveEntered = CountDownLatch(1)
        val saveGate = CountDownLatch(1)
        storage.saveEntered = saveEntered
        storage.saveGate = saveGate
        val history = launch(Dispatchers.IO) { syncer.sync() }
        assertTrue(saveEntered.await(5, TimeUnit.SECONDS))

        nodeGate.complete(Unit)
        delay(200)
        saveGate.countDown()
        withTimeout(5_000) { fees.join(); history.join() }

        assertEquals(BigInteger("2000000"), storage.transactions.getValue(hash).fee)
    }

    @Test
    fun syncFees_historyUpdatesResolvedTransactionDuringPass_lastEmissionIsTheNewerRow() = runBlocking {
        val sender = "thor1t60f02r8jvzjrhtnjgfj4ne6rs5wjnejwmj7fh"
        val api = FakeMidgardApi()
        val storage = FakeSyncerStorage()
        val node = FakeNodeApi()
        api.pages[null] = page(listOf(action("A", 2_000, "pending", sender), action("B", 1_000, address = sender)), null)
        node.responses["A"] = MainnetFixtures.THOR_SEND
        node.responses["B"] = MainnetFixtures.THOR_SEND
        val lookupB = CompletableDeferred<Unit>()
        node.gates["B"] = lookupB
        val syncer = syncer(api, storage, node, wallet = sender)
        val emissions = ConcurrentLinkedQueue<List<Transaction>>()
        val collector = launch(Dispatchers.Unconfined) { syncer.transactionsFlow.collect { emissions.add(it) } }
        syncer.sync()

        val fees = launch(Dispatchers.IO) { syncer.syncFees() }
        withTimeout(5_000) { while ("B" !in node.calls) delay(10) }
        api.pages[null] = page(listOf(action("A", 2_000, address = sender), action("B", 1_000, address = sender)), null)
        withTimeout(5_000) { syncer.sync() }
        lookupB.complete(Unit)
        withTimeout(5_000) { fees.join() }
        collector.cancel()

        val lastA = emissions.flatten().last { it.hash == "A" }
        assertEquals("success", lastA.status)
        assertEquals(BigInteger("2000000"), lastA.fee)
    }

    @Test
    fun stop_duringSyncRound_roundDoesNotReportSynced() = runBlocking {
        val api = FakeMidgardApi()
        val gate = CompletableDeferred<Unit>()
        api.pageGate = gate
        val syncer = syncer(api, FakeSyncerStorage())
        val round = launch(start = CoroutineStart.UNDISPATCHED) { syncer.sync() }

        syncer.stop()
        gate.complete(Unit)
        round.join()

        assertFalse(syncer.syncState is SyncState.Synced)
    }

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
