package io.horizontalsystems.thorchainkit

import androidx.sqlite.execSQL
import io.horizontalsystems.sqlcipher.SqlCipherDriver
import io.horizontalsystems.thorchainkit.database.MainDatabase
import io.horizontalsystems.thorchainkit.models.Transaction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class MainDatabaseMigrationTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val databaseName = "migration-test"
    private val databaseKey = ByteArray(32) { it.toByte() }

    @Test
    fun open_version1Database_migratesToVersion4PreservingRows() {
        val context = PlatformContext(tmp.root)
        createVersion1Database(context)

        val database = MainDatabase.getInstance(context, databaseName, databaseKey)
        val transactions = database.transactionDao().getAll().associateBy { it.hash }
        val syncState = database.transactionDao().getSyncState()
        database.close()

        assertEquals(setOf("sent", "failed-action"), transactions.keys)
        assertEquals(1_700_000_000L, transactions.getValue("sent").timestamp)
        assertEquals("success", transactions.getValue("sent").status)
        assertEquals(1_700_000_001L, transactions.getValue("failed-action").timestamp)
        assertEquals(Transaction.STATUS_FAILED, transactions.getValue("failed-action").status)
        assertEquals(1_700_000_001L, syncState?.lastTimestamp)
        assertNull(syncState?.backfillPageToken)
    }

    // The v4 schema comes from Room's own generated createAllTables; v1 lacked backfillPageToken.
    private fun createVersion1Database(context: PlatformContext) {
        MainDatabase.getInstance(context, databaseName, databaseKey).apply {
            transactionDao().getAll()
            close()
        }
        SqlCipherDriver(databaseKey).open(File(tmp.root, databaseName).absolutePath).use { connection ->
            connection.execSQL("ALTER TABLE TransactionSyncState DROP COLUMN backfillPageToken")
            connection.execSQL(
                "INSERT INTO `Transaction` VALUES ('sent', 10, 1700000000000, 'send', 'success', NULL, '[]', '[]')"
            )
            connection.execSQL(
                "INSERT INTO `Transaction` VALUES ('failed-action', 11, 1700000001000, 'failed', 'success', NULL, '[]', '[]')"
            )
            connection.execSQL("INSERT INTO TransactionSyncState (lastTimestamp, id) VALUES (1700000001000, '')")
            connection.execSQL("PRAGMA user_version = 1")
        }
    }
}
