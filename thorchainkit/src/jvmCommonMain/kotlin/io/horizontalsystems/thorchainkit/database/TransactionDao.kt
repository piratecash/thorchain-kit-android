package io.horizontalsystems.thorchainkit.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import io.horizontalsystems.thorchainkit.models.Transaction
import io.horizontalsystems.thorchainkit.models.TransactionSyncState

@Dao
interface TransactionDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(transactions: List<Transaction>)

    @Query("SELECT * FROM `Transaction` ORDER BY timestamp DESC, hash DESC")
    fun getAll(): List<Transaction>

    @Query("SELECT * FROM `Transaction` WHERE timestamp < :fromTimestamp ORDER BY timestamp DESC, hash DESC LIMIT :limit")
    fun getTransactions(fromTimestamp: Long, limit: Int): List<Transaction>

    @Query("SELECT * FROM `Transaction` WHERE status = 'pending'")
    fun getPending(): List<Transaction>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(syncState: TransactionSyncState)

    @Query("SELECT * FROM TransactionSyncState")
    fun getSyncState(): TransactionSyncState?
}
