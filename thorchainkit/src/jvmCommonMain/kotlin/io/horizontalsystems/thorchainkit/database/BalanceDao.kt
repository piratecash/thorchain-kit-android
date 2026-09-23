package io.horizontalsystems.thorchainkit.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import io.horizontalsystems.thorchainkit.models.Balance

@Dao
interface BalanceDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(balances: List<Balance>)

    @Query("SELECT * FROM Balance")
    fun getAll(): List<Balance>

    @Query("SELECT * FROM Balance WHERE denom = :denom")
    fun getBalance(denom: String): Balance?

    @Query("DELETE FROM Balance WHERE denom NOT IN (:denoms)")
    fun deleteExcept(denoms: List<String>)
}
