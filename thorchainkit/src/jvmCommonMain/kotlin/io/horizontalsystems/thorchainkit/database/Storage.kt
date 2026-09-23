package io.horizontalsystems.thorchainkit.database

import io.horizontalsystems.thorchainkit.models.Balance
import io.horizontalsystems.thorchainkit.models.LastBlockHeight
import io.horizontalsystems.thorchainkit.models.Transaction
import io.horizontalsystems.thorchainkit.models.TransactionSyncState
import java.math.BigInteger

class Storage(
    private val database: MainDatabase
) : TransactionSyncerStorage {

    fun getLastBlockHeight(): Long? {
        return database.lastBlockHeightDao().getLastBlockHeight()?.height
    }

    fun saveLastBlockHeight(lastBlockHeight: Long) {
        database.lastBlockHeightDao().insert(LastBlockHeight(lastBlockHeight))
    }

    fun getBalances(): List<Balance> {
        return database.balanceDao().getAll()
    }

    fun getBalance(denom: String): BigInteger? {
        return database.balanceDao().getBalance(denom)?.amount
    }

    fun saveBalances(balances: List<Balance>) {
        database.balanceDao().insert(balances)
        database.balanceDao().deleteExcept(balances.map { it.denom })
    }

    fun getTransactions(fromTimestamp: Long?, limit: Int?): List<Transaction> {
        return database.transactionDao().getTransactions(
            fromTimestamp ?: Long.MAX_VALUE,
            limit ?: Int.MAX_VALUE
        )
    }

    override fun getPendingTransactions(): List<Transaction> {
        return database.transactionDao().getPending()
    }

    override fun saveTransactions(transactions: List<Transaction>) {
        database.transactionDao().insert(transactions)
    }

    override fun getTransactionSyncTimestamp(): Long? {
        return database.transactionDao().getSyncState()?.lastTimestamp
    }

    // the sync-state row carries both the watermark and the backfill token: each
    // write preserves the other field (single-row REPLACE would clobber it otherwise)
    override fun saveTransactionSyncTimestamp(timestamp: Long) {
        val current = database.transactionDao().getSyncState()
        database.transactionDao().insert(TransactionSyncState(timestamp, current?.backfillPageToken))
    }

    override fun getBackfillPageToken(): String? {
        return database.transactionDao().getSyncState()?.backfillPageToken
    }

    override fun saveBackfillPageToken(token: String?) {
        val current = database.transactionDao().getSyncState()
        database.transactionDao().insert(TransactionSyncState(current?.lastTimestamp ?: 0, token))
    }
}
