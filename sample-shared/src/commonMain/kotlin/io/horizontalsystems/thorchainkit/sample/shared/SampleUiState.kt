package io.horizontalsystems.thorchainkit.sample.shared

import io.horizontalsystems.thorchainkit.models.Transaction
import io.horizontalsystems.thorchainkit.network.Network
import java.math.BigInteger

/** Send form: kept separate so an edit can be told apart from a send in flight. */
data class SendFormState(
    val to: String = "",
    val amount: String = "",
    val memo: String = "",
    val result: String? = null
)

data class SampleUiState(
    val network: Network = Network.Mainnet,
    val mnemonic: String = DEFAULT_MNEMONIC,
    val watchAddress: String = "",
    val receiveAddress: String? = null,
    val decimals: Int = 8,
    val nativeDenom: String = "rune",
    val syncState: String = "",
    val transactionsSyncState: String = "",
    val lastBlockHeight: Long = 0L,
    val balances: Map<String, BigInteger> = emptyMap(),
    val transactions: List<Transaction> = emptyList(),
    val error: String? = null,
    val send: SendFormState = SendFormState()
) {
    val isActive: Boolean
        get() = receiveAddress != null
}

sealed interface SampleEvent {
    data class MnemonicChanged(val value: String) : SampleEvent
    data class NetworkChanged(val value: Network) : SampleEvent
    data class WatchAddressChanged(val value: String) : SampleEvent
    data class SendToChanged(val value: String) : SampleEvent
    data class SendAmountChanged(val value: String) : SampleEvent
    data class SendMemoChanged(val value: String) : SampleEvent
    data object RestoreFromMnemonic : SampleEvent
    data object StartWatch : SampleEvent
    data object Send : SampleEvent
    data object Stop : SampleEvent
}
