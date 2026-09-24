package io.horizontalsystems.thorchainkit.database

import io.horizontalsystems.thorchainkit.models.Transaction
import java.math.BigInteger

// the slice of Storage that TransactionSyncer depends on — an interface so the
// sync logic is unit-testable without a Room database
internal interface TransactionSyncerStorage {

    fun getTransactionSyncTimestamp(): Long?

    fun saveTransactionSyncTimestamp(timestamp: Long)

    fun getBackfillPageToken(): String?

    fun saveBackfillPageToken(token: String?)

    fun getPendingTransactions(): List<Transaction>

    // Midgard carries no fee: a re-saved transaction keeps the fee already stored for it.
    // Returns the rows as stored.
    fun saveTransactions(transactions: List<Transaction>): List<Transaction>

    // registers each hash once; a resolved lookup is never reopened
    fun addFeeLookups(hashes: List<String>)

    // unresolved lookups, newest first
    fun getFeeLookups(limit: Int): List<Transaction>

    // stores the fee, if any, and resolves the lookup for good; returns the stored row
    fun saveFee(hash: String, fee: BigInteger?): Transaction?
}
