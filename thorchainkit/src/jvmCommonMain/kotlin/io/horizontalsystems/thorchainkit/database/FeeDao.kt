package io.horizontalsystems.thorchainkit.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import io.horizontalsystems.thorchainkit.models.FeeLookup
import io.horizontalsystems.thorchainkit.models.Transaction
import java.math.BigInteger

@Dao
internal interface FeeDao {

    @Query("SELECT * FROM `Transaction` WHERE hash = :hash")
    fun getTransaction(hash: String): Transaction?

    @Query("SELECT * FROM `Transaction` WHERE fee IS NOT NULL AND hash IN (:hashes)")
    fun getTransactionsWithFee(hashes: List<String>): List<Transaction>

    @Query("UPDATE `Transaction` SET fee = :fee WHERE hash = :hash")
    fun updateFee(hash: String, fee: BigInteger)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insertFeeLookups(lookups: List<FeeLookup>)

    @Query(
        "SELECT `Transaction`.* FROM `Transaction` INNER JOIN FeeLookup ON FeeLookup.hash = `Transaction`.hash " +
            "WHERE FeeLookup.resolved = 0 ORDER BY `Transaction`.timestamp DESC LIMIT :limit"
    )
    fun getUnresolvedFeeLookups(limit: Int): List<Transaction>

    @Query("UPDATE FeeLookup SET resolved = 1 WHERE hash = :hash")
    fun resolveFeeLookup(hash: String)
}
