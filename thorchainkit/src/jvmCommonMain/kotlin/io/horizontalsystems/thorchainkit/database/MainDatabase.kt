package io.horizontalsystems.thorchainkit.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL
import io.horizontalsystems.thorchainkit.DatabaseKeyMismatchException
import io.horizontalsystems.thorchainkit.PlatformContext
import io.horizontalsystems.thorchainkit.models.Balance
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
    ],
    version = 4,
    exportSchema = false
)
@TypeConverters(RoomTypeConverters::class)
abstract class MainDatabase : RoomDatabase() {

    abstract fun lastBlockHeightDao(): LastBlockHeightDao
    abstract fun balanceDao(): BalanceDao
    abstract fun transactionDao(): TransactionDao

    companion object {

        internal val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(connection: SQLiteConnection) {
                connection.execSQL("ALTER TABLE TransactionSyncState ADD COLUMN backfillPageToken TEXT")
            }
        }

        // timestamps were stored in milliseconds (Midgard nanoseconds / 1_000_000)
        // while consumers expect unix seconds
        internal val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(connection: SQLiteConnection) {
                connection.execSQL("UPDATE `Transaction` SET timestamp = timestamp / 1000")
                connection.execSQL("UPDATE TransactionSyncState SET lastTimestamp = lastTimestamp / 1000")
            }
        }

        // failed actions were stored with Midgard's indexing status "success"
        internal val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(connection: SQLiteConnection) {
                connection.execSQL("UPDATE `Transaction` SET status = 'failed' WHERE type = 'failed'")
            }
        }

        internal val MIGRATIONS: Array<Migration> = arrayOf(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)

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
