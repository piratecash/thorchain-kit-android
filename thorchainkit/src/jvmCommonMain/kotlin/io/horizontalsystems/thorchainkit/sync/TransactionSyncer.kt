package io.horizontalsystems.thorchainkit.sync

import io.horizontalsystems.thorchainkit.ThorchainKit.SyncError
import io.horizontalsystems.thorchainkit.ThorchainKit.SyncState
import io.horizontalsystems.thorchainkit.database.TransactionSyncerStorage
import io.horizontalsystems.thorchainkit.models.CoinTransfer
import io.horizontalsystems.thorchainkit.models.Transaction
import io.horizontalsystems.thorchainkit.network.ActionTx
import io.horizontalsystems.thorchainkit.network.InvalidProviderResponse
import io.horizontalsystems.thorchainkit.network.MidgardAction
import io.horizontalsystems.thorchainkit.network.MidgardProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import java.math.BigInteger

class TransactionSyncer(
    private val address: String,
    private val midgardProvider: MidgardProvider,
    private val storage: TransactionSyncerStorage
) {

    companion object {
        private const val PAGE_LIMIT = 50
        private const val MAX_PAGES = 20
        private const val PENDING_REFRESH_LIMIT = 10

        // Midgard data is untrusted input: every required field is checked and a
        // malformed action surfaces as a typed InvalidProviderResponse instead of
        // an NPE or a silently wrong record
        fun fromMidgardAction(action: MidgardAction): Transaction? {
            val incoming = action.incoming
                ?: throw InvalidProviderResponse("midgard action: missing 'in'")
            val outgoing = action.outgoing
                ?: throw InvalidProviderResponse("midgard action: missing 'out'")

            val hash = incoming.firstOrNull { !it.txId.isNullOrEmpty() }?.txId
                ?: outgoing.firstOrNull { !it.txId.isNullOrEmpty() }?.txId
                ?: return null

            val type = action.type
                ?: throw InvalidProviderResponse("midgard action: missing type")

            return Transaction(
                hash = hash,
                blockHeight = action.height
                    ?: throw InvalidProviderResponse("midgard action: missing height"),
                // Midgard reports date in nanoseconds; consumers expect unix seconds
                timestamp = (action.date
                    ?: throw InvalidProviderResponse("midgard action: missing date")) / 1_000_000_000,
                type = type,
                status = status(type, action.status
                    ?: throw InvalidProviderResponse("midgard action: missing status")),
                memo = extractMemo(action),
                incoming = coinTransfers(incoming),
                outgoing = coinTransfers(outgoing)
            )
        }

        // Midgard's status only says whether indexing finished: an action that was
        // included in a block but failed to execute comes as type "failed" with status
        // "success". Folding that into status keeps consumers from showing it as a
        // completed transfer.
        private fun status(type: String, midgardStatus: String): String =
            if (type == Transaction.TYPE_FAILED) Transaction.STATUS_FAILED else midgardStatus

        private fun coinTransfers(txs: List<ActionTx>): List<CoinTransfer> {
            return txs.flatMap { tx ->
                val address = tx.address
                    ?: throw InvalidProviderResponse("midgard action: missing address")

                tx.coins.orEmpty().map { coin ->
                    val asset = coin.asset
                        ?: throw InvalidProviderResponse("midgard action: missing coin asset")
                    val amountString = coin.amount
                        ?: throw InvalidProviderResponse("midgard action: missing coin amount")
                    val amount = try {
                        BigInteger(amountString)
                    } catch (error: NumberFormatException) {
                        throw InvalidProviderResponse("midgard action: invalid amount: $amountString")
                    }

                    CoinTransfer(address, asset, amount)
                }
            }
        }

        // memo sits inside the type-specific metadata object: metadata.send.memo, metadata.swap.memo, ...
        private fun extractMemo(action: MidgardAction): String? {
            val metadata = action.metadata ?: return null

            metadata.entrySet().forEach { (_, value) ->
                if (value.isJsonObject) {
                    val memo = value.asJsonObject.get("memo")
                    if (memo != null && memo.isJsonPrimitive) {
                        return memo.asString
                    }
                }
            }
            return null
        }
    }

    private val _syncStateFlow: MutableStateFlow<SyncState> = MutableStateFlow(SyncState.NotSynced(SyncError.NotStarted()))
    val syncStateFlow: StateFlow<SyncState> = _syncStateFlow

    val syncState: SyncState
        get() = _syncStateFlow.value

    private val _transactionsFlow = MutableSharedFlow<List<Transaction>>(replay = 0, extraBufferCapacity = 10)
    val transactionsFlow: SharedFlow<List<Transaction>> = _transactionsFlow

    suspend fun sync() {
        _syncStateFlow.update { SyncState.Syncing() }

        try {
            val recentHashes = syncRecent()
            syncBackfill()
            refreshPending(recentHashes)

            _syncStateFlow.update { SyncState.Synced() }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            _syncStateFlow.update { SyncState.NotSynced(error) }
        }
    }

    // Pages from the newest action towards the last-synced watermark.
    // Returns the tx hashes seen this round.
    private suspend fun syncRecent(): Set<String> {
        val lastSyncedTimestamp = storage.getTransactionSyncTimestamp() ?: 0
        val updated = mutableListOf<Transaction>()
        var nextPageToken: String? = null
        var reachedSynced = false
        var exhausted = false

        for (page in 0 until MAX_PAGES) {
            val (actions, token) = midgardProvider.fetchActions(address, PAGE_LIMIT, nextPageToken)
            val transactions = actions.mapNotNull { fromMidgardAction(it) }

            updated.addAll(transactions)

            reachedSynced = transactions.any { it.timestamp <= lastSyncedTimestamp && !it.isPending }
            nextPageToken = token
            exhausted = token == null || actions.isEmpty()

            if (reachedSynced || exhausted) break
        }

        if (updated.isNotEmpty()) {
            storage.saveTransactions(updated)
        }

        // Page cap hit before connecting to already-synced history: remember where to
        // resume so the older history is backfilled instead of silently skipped.
        // Written BEFORE the watermark advances: if we die between the two writes, a
        // stale watermark merely re-fetches, while a missing backfill token would
        // permanently lose history. Clearing the token is left to syncBackfill alone —
        // a transient empty page from a lagging mirror must not abort a pending backfill.
        if (!reachedSynced && !exhausted && nextPageToken != null) {
            storage.saveBackfillPageToken(nextPageToken)
        }

        if (updated.isNotEmpty()) {
            val maxConfirmedTimestamp = updated.filter { !it.isPending }.maxOfOrNull { it.timestamp }
            if (maxConfirmedTimestamp != null && maxConfirmedTimestamp > lastSyncedTimestamp) {
                storage.saveTransactionSyncTimestamp(maxConfirmedTimestamp)
            }

            // last: emit can suspend when a subscriber's buffer is full — no persistence
            // may depend on code after this point
            _transactionsFlow.emit(updated)
        }

        return updated.map { it.hash }.toSet()
    }

    // Continues fetching history older than a previous round's page cap, a bounded
    // number of pages per round, until the gap is closed.
    private suspend fun syncBackfill() {
        var token: String? = storage.getBackfillPageToken() ?: return
        val updated = mutableListOf<Transaction>()

        for (page in 0 until MAX_PAGES) {
            val (actions, next) = midgardProvider.fetchActions(address, PAGE_LIMIT, token)

            updated.addAll(actions.mapNotNull { fromMidgardAction(it) })

            token = next
            if (next == null || actions.isEmpty()) {
                token = null
                break
            }
        }

        if (updated.isNotEmpty()) {
            storage.saveTransactions(updated)
        }

        storage.saveBackfillPageToken(token)

        if (updated.isNotEmpty()) {
            _transactionsFlow.emit(updated)
        }
    }

    // Stored pending transactions that no longer show up in the recent pages would
    // otherwise stay "pending" forever — re-query them by txid.
    private suspend fun refreshPending(recentHashes: Set<String>) {
        val stale = storage.getPendingTransactions()
            .filter { it.hash !in recentHashes }
            .take(PENDING_REFRESH_LIMIT)

        if (stale.isEmpty()) return

        val refreshed = mutableListOf<Transaction>()

        stale.forEach { pending ->
            val (actions, _) = midgardProvider.fetchActions(address, limit = 10, txId = pending.hash)

            actions.mapNotNull { fromMidgardAction(it) }
                .filter { it.hash.equals(pending.hash, ignoreCase = true) }
                // normalize to the stored hash: it is the primary key, and a REPLACE
                // under different casing would duplicate the row instead of updating it
                .forEach { refreshed.add(it.copy(hash = pending.hash)) }
        }

        if (refreshed.isNotEmpty()) {
            storage.saveTransactions(refreshed)
            _transactionsFlow.emit(refreshed)
        }
    }
}
