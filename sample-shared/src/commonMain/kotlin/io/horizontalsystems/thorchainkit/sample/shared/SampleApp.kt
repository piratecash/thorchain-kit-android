package io.horizontalsystems.thorchainkit.sample.shared

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.horizontalsystems.thorchainkit.models.Transaction
import io.horizontalsystems.thorchainkit.network.Network

/** Top-level sample UI: state flows down, events flow up through [onEvent]. */
@Composable
fun SampleApp(state: SampleUiState, onEvent: (SampleEvent) -> Unit, modifier: Modifier = Modifier) {
    MaterialTheme {
        Surface(modifier = modifier.fillMaxSize()) {
            if (state.isActive) {
                WalletScreen(state, onEvent)
            } else {
                SetupScreen(state, onEvent)
            }
        }
    }
}

@Composable
fun SetupScreen(state: SampleUiState, onEvent: (SampleEvent) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text("ThorchainKit Sample", style = MaterialTheme.typography.titleLarge)

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(Network.Mainnet, Network.MayaMainnet).forEach { network ->
                OutlinedButton(onClick = { onEvent(SampleEvent.NetworkChanged(network)) }) {
                    val mark = if (network == state.network) "✓ " else ""
                    Text(mark + network.displayName)
                }
            }
        }

        OutlinedTextField(
            value = state.mnemonic,
            onValueChange = { onEvent(SampleEvent.MnemonicChanged(it)) },
            label = { Text("Mnemonic") },
            modifier = Modifier.fillMaxWidth()
        )
        Button(onClick = { onEvent(SampleEvent.RestoreFromMnemonic) }, modifier = Modifier.fillMaxWidth()) {
            Text("Restore")
        }

        OutlinedTextField(
            value = state.watchAddress,
            onValueChange = { onEvent(SampleEvent.WatchAddressChanged(it)) },
            label = { Text("Watch address (thor1...)") },
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedButton(onClick = { onEvent(SampleEvent.StartWatch) }, modifier = Modifier.fillMaxWidth()) {
            Text("Watch")
        }

        state.error?.let { Text("Error: $it", color = MaterialTheme.colorScheme.error) }
    }
}

@Composable
fun WalletScreen(state: SampleUiState, onEvent: (SampleEvent) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text("${state.network.displayName} · block ${state.lastBlockHeight}", style = MaterialTheme.typography.bodySmall)
        Text(state.receiveAddress.orEmpty(), style = MaterialTheme.typography.bodyMedium)
        Text("Sync: ${state.syncState}", style = MaterialTheme.typography.bodySmall)
        Text("Tx sync: ${state.transactionsSyncState}", style = MaterialTheme.typography.bodySmall)

        HorizontalDivider()
        BalancesSection(state.balances, state.decimals)

        HorizontalDivider()
        SendSection(state.send, state.nativeDenom, onEvent)

        HorizontalDivider()
        TransactionsSection(state.transactions)

        HorizontalDivider()
        OutlinedButton(onClick = { onEvent(SampleEvent.Stop) }, modifier = Modifier.fillMaxWidth()) {
            Text("Stop")
        }
    }
}

@Composable
fun BalancesSection(balances: Map<String, java.math.BigInteger>, decimals: Int) {
    Text("Balances", style = MaterialTheme.typography.titleMedium)
    if (balances.isEmpty()) {
        Text("no balances", style = MaterialTheme.typography.bodySmall)
    }
    balances.toSortedMap().forEach { (denom, amount) ->
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(denom)
            Text(amount.toDisplay(decimals))
        }
    }
}

@Composable
fun SendSection(send: SendFormState, nativeDenom: String, onEvent: (SampleEvent) -> Unit) {
    val symbol = nativeDenom.uppercase()

    Text("Send $symbol", style = MaterialTheme.typography.titleMedium)
    OutlinedTextField(
        value = send.to,
        onValueChange = { onEvent(SampleEvent.SendToChanged(it)) },
        label = { Text("To") },
        modifier = Modifier.fillMaxWidth()
    )
    OutlinedTextField(
        value = send.amount,
        onValueChange = { onEvent(SampleEvent.SendAmountChanged(it)) },
        label = { Text("Amount ($symbol)") },
        modifier = Modifier.fillMaxWidth()
    )
    OutlinedTextField(
        value = send.memo,
        onValueChange = { onEvent(SampleEvent.SendMemoChanged(it)) },
        label = { Text("Memo (optional)") },
        modifier = Modifier.fillMaxWidth()
    )
    Button(
        onClick = { onEvent(SampleEvent.Send) },
        enabled = send.to.isNotBlank() && send.amount.isNotBlank(),
        modifier = Modifier.fillMaxWidth()
    ) {
        Text("Send")
    }
    send.result?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
}

@Composable
fun TransactionsSection(transactions: List<Transaction>) {
    Text("Transactions", style = MaterialTheme.typography.titleMedium)
    transactions.forEach { tx ->
        Column(modifier = Modifier.fillMaxWidth()) {
            Text("${tx.type} · ${tx.status}", style = MaterialTheme.typography.bodyMedium)
            Text(tx.hash.take(16) + "…", style = MaterialTheme.typography.bodySmall)
            tx.memo?.takeIf { it.isNotBlank() }?.let {
                Text("memo: $it", style = MaterialTheme.typography.bodySmall)
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
    }
}

// mainnet chains shown in the sample selector (stagenet is a kit config option, not surfaced here)
private val Network.displayName: String
    get() = when (this) {
        Network.Mainnet -> "THORChain"
        Network.MayaMainnet -> "Mayachain"
        else -> name
    }
