package io.horizontalsystems.thorchainkit.models

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.math.BigInteger

@Entity
data class Transaction(
    @PrimaryKey
    val hash: String,
    val blockHeight: Long,
    val timestamp: Long,
    val type: String,
    val status: String,
    val memo: String?,
    val incoming: List<CoinTransfer>,
    val outgoing: List<CoinTransfer>,
    // what the wallet paid for its own transaction, in native base units; null for incoming ones
    // and when the node cannot tell
    public val fee: BigInteger? = null
) {

    val isPending: Boolean
        get() = status == STATUS_PENDING

    val isFailed: Boolean
        get() = status == STATUS_FAILED

    companion object {
        const val STATUS_PENDING = "pending"
        const val STATUS_FAILED = "failed"
        const val TYPE_FAILED = "failed"
    }
}

data class CoinTransfer(
    val address: String,
    val asset: String,
    val amount: BigInteger
)
