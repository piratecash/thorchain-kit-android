package io.horizontalsystems.thorchainkit.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import io.horizontalsystems.thorchainkit.DatabaseKeyMismatchException
import io.horizontalsystems.thorchainkit.PlatformContext
import io.horizontalsystems.thorchainkit.models.Balance
import io.horizontalsystems.thorchainkit.models.FeeLookup
import io.horizontalsystems.thorchainkit.models.LastBlockHeight
import io.horizontalsystems.thorchainkit.models.Transaction
import io.horizontalsystems.thorchainkit.models.TransactionSyncState
import java.io.File

@Database(
    entities = [
        LastBlockHeight::class,
        Balance::class,
        Transaction::class,
        TransactionSyncState::class,
        FeeLookup::class,
    ],
    version = 5,
    exportSchema = false
)
@TypeConverters(RoomTypeConverters::class)
abstract class MainDatabase : RoomDatabase() {

    abstract fun lastBlockHeightDao(): LastBlockHeightDao
    abstract fun balanceDao(): BalanceDao
    abstract fun transactionDao(): TransactionDao
    internal abstract fun feeDao(): FeeDao

    companion object {

        /**
         * Opens a new instance on every call, under the same file rules as the kit: [databaseKey] is a
         * raw 32-byte SQLCipher key, a plaintext file is deleted and recreated encrypted, and a file
         * encrypted with another key fails with [DatabaseKeyMismatchException]. The kit closes the
         * instance when the database is cleared or reopened under another key.
         */
        public fun getInstance(context: PlatformContext, databaseName: String, databaseKey: ByteArray): MainDatabase {
            return ThorchainDatabaseManager.openExternal(context, databaseName, databaseKey)
        }
    }
}

internal expect fun mainDatabaseFile(context: PlatformContext, databaseName: String): File

internal expect fun buildMainDatabase(context: PlatformContext, databaseName: String, databaseKey: ByteArray): MainDatabase

// Throws when databaseKey cannot read the encrypted file; must not modify it.
internal expect fun verifyMainDatabaseKey(file: File, databaseKey: ByteArray)

internal expect fun deleteMainDatabase(context: PlatformContext, databaseName: String)
