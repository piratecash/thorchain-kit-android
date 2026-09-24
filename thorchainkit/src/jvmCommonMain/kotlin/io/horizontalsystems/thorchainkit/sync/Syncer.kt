package io.horizontalsystems.thorchainkit.sync

import io.horizontalsystems.thorchainkit.ThorchainKit.SyncError
import io.horizontalsystems.thorchainkit.ThorchainKit.SyncState
import io.horizontalsystems.thorchainkit.account.BalanceManager
import io.horizontalsystems.thorchainkit.database.Storage
import io.horizontalsystems.thorchainkit.models.Address
import io.horizontalsystems.thorchainkit.network.ThornodeApiProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

class Syncer internal constructor(
    private val address: Address,
    private val syncTimer: SyncTimer,
    private val thornodeApiProvider: ThornodeApiProvider,
    private val balanceManager: BalanceManager,
    private val transactionSyncer: TransactionSyncer,
    private val storage: Storage
) : SyncTimer.Listener {

    private val syncing = AtomicBoolean(false)
    private val feeSyncing = AtomicBoolean(false)
    private var scope: CoroutineScope? = null

    var syncState: SyncState = SyncState.NotSynced(SyncError.NotStarted())
        private set(value) {
            if (value != field) {
                field = value
                _syncStateFlow.update { value }
            }
        }

    var lastBlockHeight: Long = storage.getLastBlockHeight() ?: 0
        private set(value) {
            if (value != field) {
                field = value
                _lastBlockHeightFlow.update { value }
            }
        }

    private val _syncStateFlow = MutableStateFlow(syncState)
    val syncStateFlow: StateFlow<SyncState> = _syncStateFlow

    private val _lastBlockHeightFlow = MutableStateFlow(lastBlockHeight)
    val lastBlockHeightFlow: StateFlow<Long> = _lastBlockHeightFlow

    fun start(scope: CoroutineScope) {
        this.scope = scope
        syncTimer.start(this, scope)
    }

    fun stop() {
        syncState = SyncState.NotSynced(SyncError.NotStarted())
        transactionSyncer.stop()
        syncTimer.stop()
    }

    fun pause() {
        syncTimer.pause()
    }

    fun resume() {
        syncTimer.resume()
    }

    fun refresh() {
        when (syncTimer.state) {
            SyncTimer.State.Ready -> sync()
            is SyncTimer.State.NotReady -> scope?.let { syncTimer.start(this, it) }
        }
    }

    override fun onUpdateSyncTimerState(state: SyncTimer.State) {
        syncState = when (state) {
            is SyncTimer.State.NotReady -> SyncState.NotSynced(state.error)
            SyncTimer.State.Ready -> SyncState.Syncing()
        }
    }

    override fun sync() {
        val scope = this.scope ?: return
        if (!syncing.compareAndSet(false, true)) return

        scope.launch {
            try {
                performSync()
            } finally {
                syncing.set(false)
            }
            syncFees()
        }
    }

    // single-flight and outside `syncing`: a slow fee lookup must not hold up the next history round
    private suspend fun syncFees() {
        if (syncState !is SyncState.Synced || !feeSyncing.compareAndSet(false, true)) return

        try {
            transactionSyncer.syncFees()
        } finally {
            feeSyncing.set(false)
        }
    }

    private suspend fun performSync() {
        try {
            val blockHeight = thornodeApiProvider.fetchLastBlockHeight()
            if (blockHeight != lastBlockHeight) {
                storage.saveLastBlockHeight(blockHeight)
                lastBlockHeight = blockHeight
            }

            balanceManager.handleBalances(thornodeApiProvider.fetchBalances(address))

            transactionSyncer.sync()

            syncState = SyncState.Synced()
        } catch (error: CancellationException) {
            // cancellation is not a sync failure — let it propagate so the coroutine
            // ends quietly instead of overwriting the state after stop()
            throw error
        } catch (error: Throwable) {
            syncState = SyncState.NotSynced(error)
        }
    }
}
