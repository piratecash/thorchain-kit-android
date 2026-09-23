package io.horizontalsystems.thorchainkit.sample.shared

import io.horizontalsystems.hdwalletkit.Mnemonic
import io.horizontalsystems.thorchainkit.ThorchainKit
import io.horizontalsystems.thorchainkit.models.Address
import io.horizontalsystems.thorchainkit.network.Network
import io.horizontalsystems.thorchainkit.transaction.Signer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

// public test vector; never a funded mainnet mnemonic
const val DEFAULT_MNEMONIC: String =
    "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon about"

/**
 * Persisted sample session storage: Android backs this with SharedPreferences, desktop with a
 * private properties file. Never stores anything beyond what a demo app should — the mnemonic
 * itself is only as safe as this store, see [SampleController]'s KDoc.
 */
interface SessionStore {
    fun get(key: String): String?
    fun put(key: String, value: String)
    fun remove(key: String)
}

/**
 * Drives the ThorchainKit sample screens: mnemonic restore, watch-only restore, send, stop, and
 * resuming the last session after process death. State flows down as [SampleUiState]; the UI
 * only calls back through [onEvent].
 *
 * NOTE: storing a plaintext mnemonic via [SessionStore] is fine for a demo, but a real wallet
 * must keep it in encrypted/keystore-backed storage. The mnemonic is never logged.
 */
class SampleController(
    private val sessionStore: SessionStore,
    private val kitFactory: (Address, Network) -> ThorchainKit,
    private val scope: CoroutineScope
) {

    private val _uiState = MutableStateFlow(initialState())
    val uiState: StateFlow<SampleUiState> = _uiState.asStateFlow()

    private var kit: ThorchainKit? = null
    private var signer: Signer? = null

    init {
        when (sessionStore.get(KEY_MODE)) {
            MODE_MNEMONIC -> restoreFromMnemonic(persist = false)
            MODE_WATCH -> startWatch(persist = false)
        }
    }

    fun onEvent(event: SampleEvent) {
        when (event) {
            is SampleEvent.MnemonicChanged -> updateMnemonic(event.value)
            is SampleEvent.NetworkChanged -> updateNetwork(event.value)
            is SampleEvent.WatchAddressChanged -> updateWatchAddress(event.value)
            is SampleEvent.SendToChanged -> _uiState.update { it.copy(send = it.send.copy(to = event.value)) }
            is SampleEvent.SendAmountChanged -> _uiState.update { it.copy(send = it.send.copy(amount = event.value)) }
            is SampleEvent.SendMemoChanged -> _uiState.update { it.copy(send = it.send.copy(memo = event.value)) }
            SampleEvent.RestoreFromMnemonic -> restoreFromMnemonic()
            SampleEvent.StartWatch -> startWatch()
            SampleEvent.Send -> send()
            SampleEvent.Stop -> stop()
        }
    }

    private fun updateMnemonic(value: String) {
        sessionStore.put(KEY_MNEMONIC, value)
        _uiState.update { it.copy(mnemonic = value) }
    }

    private fun updateNetwork(value: Network) {
        sessionStore.put(KEY_NETWORK, value.name)
        _uiState.update { it.copy(network = value) }
    }

    private fun updateWatchAddress(value: String) {
        sessionStore.put(KEY_WATCH, value)
        _uiState.update { it.copy(watchAddress = value) }
    }

    private fun restoreFromMnemonic(persist: Boolean = true) {
        val state = _uiState.value
        runCatching {
            val seed = Mnemonic().toSeed(state.mnemonic.trim().split(Regex("\\s+")))
            val signer = Signer.getInstance(seed, state.network)
            val address = ThorchainKit.getAddress(seed, state.network)
            start(address, state.network)
            this.signer = signer
            if (persist) sessionStore.put(KEY_MODE, MODE_MNEMONIC)
        }.onFailure(::reportError)
    }

    private fun startWatch(persist: Boolean = true) {
        val state = _uiState.value
        runCatching {
            signer = null
            val address = Address.fromString(state.watchAddress.trim(), state.network)
            start(address, state.network)
            if (persist) sessionStore.put(KEY_MODE, MODE_WATCH)
        }.onFailure(::reportError)
    }

    private fun start(address: Address, network: Network) {
        val kit = kitFactory(address, network)
        this.kit = kit
        kit.start()

        _uiState.update {
            it.copy(
                receiveAddress = kit.receiveAddress,
                decimals = kit.decimals,
                nativeDenom = kit.nativeDenom,
                error = null
            )
        }

        scope.launch { kit.syncStateFlow.collect { s -> _uiState.update { it.copy(syncState = s.toString()) } } }
        scope.launch { kit.transactionsSyncStateFlow.collect { s -> _uiState.update { it.copy(transactionsSyncState = s.toString()) } } }
        scope.launch { kit.lastBlockHeightFlow.collect { h -> _uiState.update { it.copy(lastBlockHeight = h) } } }
        scope.launch { kit.balancesFlow.collect { b -> _uiState.update { it.copy(balances = b) } } }
        scope.launch {
            kit.transactionsFlow.collect { _uiState.update { it.copy(transactions = kit.getTransactions(limit = 20)) } }
        }
    }

    private fun send() {
        val kit = kit ?: return
        val signer = signer ?: run {
            _uiState.update { it.copy(send = it.send.copy(result = "watch-only: no signer")) }
            return
        }
        val form = _uiState.value.send
        val to = form.to.trim()
        val amountText = form.amount

        scope.launch {
            val result = runCatching {
                val txHash = kit.send(
                    to = Address.fromString(to, kit.network),
                    amount = amountText.parseAmount(kit.decimals),
                    memo = form.memo.ifBlank { null },
                    signer = signer
                )
                "sent: $txHash"
            }.getOrElse { "error: ${it.message}" }
            _uiState.update { it.copy(send = it.send.copy(result = result)) }
        }
    }

    private fun stop() {
        kit?.stop()
        kit = null
        signer = null
        sessionStore.remove(KEY_MODE)
        _uiState.update {
            initialState().copy(
                network = it.network,
                mnemonic = it.mnemonic,
                watchAddress = it.watchAddress
            )
        }
    }

    private fun reportError(t: Throwable) {
        _uiState.update { it.copy(error = t.message ?: t.toString()) }
    }

    private fun initialState(): SampleUiState = SampleUiState(
        network = readNetwork(),
        mnemonic = sessionStore.get(KEY_MNEMONIC) ?: DEFAULT_MNEMONIC,
        watchAddress = sessionStore.get(KEY_WATCH) ?: ""
    )

    private fun readNetwork(): Network =
        sessionStore.get(KEY_NETWORK)
            ?.let { name -> runCatching { Network.valueOf(name) }.getOrNull() }
            ?: Network.Mainnet

    companion object {
        private const val KEY_NETWORK = "network"
        private const val KEY_MNEMONIC = "mnemonic"
        private const val KEY_WATCH = "watch_address"
        private const val KEY_MODE = "session_mode"
        private const val MODE_MNEMONIC = "mnemonic"
        private const val MODE_WATCH = "watch"
    }
}
