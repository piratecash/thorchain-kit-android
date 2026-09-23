package io.horizontalsystems.thorchainkit.database

import io.horizontalsystems.thorchainkit.models.Transaction

// the slice of Storage that TransactionSyncer depends on — an interface so the
// sync logic is unit-testable without a Room database
interface TransactionSyncerStorage {

    fun getTransactionSyncTimestamp(): Long?

    fun saveTransactionSyncTimestamp(timestamp: Long)

    fun getBackfillPageToken(): String?

    fun saveBackfillPageToken(token: String?)

    fun getPendingTransactions(): List<Transaction>

    fun saveTransactions(transactions: List<Transaction>)
}
