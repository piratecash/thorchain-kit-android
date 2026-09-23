package io.horizontalsystems.thorchainkit.sample.shared

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import java.math.BigInteger

@Preview
@Composable
private fun BalancesSectionPreview() {
    MaterialTheme {
        Surface {
            BalancesSection(balances = mapOf("rune" to BigInteger("123450000")), decimals = 8)
        }
    }
}

@Preview
@Composable
private fun SendSectionPreview() {
    MaterialTheme {
        Surface {
            SendSection(send = SendFormState(to = "thor1...", amount = "1.5"), nativeDenom = "rune", onEvent = {})
        }
    }
}

@Preview
@Composable
private fun SetupScreenPreview() {
    MaterialTheme {
        Surface {
            SetupScreen(state = SampleUiState(), onEvent = {})
        }
    }
}
