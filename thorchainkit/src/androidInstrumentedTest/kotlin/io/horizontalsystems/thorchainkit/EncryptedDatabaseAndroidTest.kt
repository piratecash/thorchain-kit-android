package io.horizontalsystems.thorchainkit

import android.content.Context
import android.content.ContextWrapper
import android.database.SQLException
import android.database.sqlite.SQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.horizontalsystems.thorchainkit.database.MainDatabase
import io.horizontalsystems.thorchainkit.database.ThorchainDatabaseManager
import io.horizontalsystems.thorchainkit.models.Address
import io.horizontalsystems.thorchainkit.models.LastBlockHeight
import io.horizontalsystems.thorchainkit.network.Network
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.net.URL
import java.security.MessageDigest
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class EncryptedDatabaseAndroidTest {

    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private val network = Network.Mainnet
    private val walletId = "encryption-test-${UUID.randomUUID()}"
    private val databaseName = "thorchain-Mainnet-$walletId"
    private val databaseFile: File = context.getDatabasePath(databaseName)
    private val keyA = ByteArray(32) { it.toByte() }
    private val keyB = ByteArray(32) { (it + 100).toByte() }
    private val plaintextHeader = "SQLite format 3\u0000".encodeToByteArray()
    private val offline = listOf(URL("http://127.0.0.1:1/"))
    private val address = Address.fromString("thor1gm00vwsfcp48enm4uv9e5dhm37jtd0ye27wrx0", network)

    private fun kit(key: ByteArray): ThorchainKit =
        ThorchainKit.getInstance(context, address, network, walletId, key, thornodeUrls = offline, midgardUrls = offline)

    private fun kitDatabase(key: ByteArray): MainDatabase =
        ThorchainDatabaseManager.getMainDatabase(context, network, walletId, key)

    private fun external(key: ByteArray): MainDatabase = MainDatabase.getInstance(context, databaseName, key)

    private fun header(): ByteArray = databaseFile.readBytes().copyOf(16)

    private fun sha256(file: File): ByteArray = MessageDigest.getInstance("SHA-256").digest(file.readBytes())

    private fun databaseFiles(): List<String> =
        databaseFile.parentFile?.listFiles().orEmpty().map { it.name }.filter { it.startsWith(databaseName) }

    @After
    fun tearDown() {
        ThorchainKit.clear(context, network, walletId)
    }

    @Test
    fun getInstance_newDatabase_writesEncryptedFileWithWorkingDao() {
        kit(keyA)
        kitDatabase(keyA).lastBlockHeightDao().insert(LastBlockHeight(42))

        assertEquals(42L, kitDatabase(keyA).lastBlockHeightDao().getLastBlockHeight()?.height)
        assertFalse(header().contentEquals(plaintextHeader))
    }

    @Test
    fun getInstance_reopenWithSameKey_seesStoredData() {
        external(keyA).apply {
            lastBlockHeightDao().insert(LastBlockHeight(42))
            close()
        }

        kit(keyA)

        assertEquals(42L, kitDatabase(keyA).lastBlockHeightDao().getLastBlockHeight()?.height)
    }

    @Test
    fun getInstance_wrongKey_throwsMismatchAndKeepsFileBytes() {
        external(keyA).apply {
            lastBlockHeightDao().insert(LastBlockHeight(42))
            close()
        }
        val before = sha256(databaseFile)

        val failure = runCatching { kit(keyB) }.exceptionOrNull()

        assertTrue(failure.toString(), failure is DatabaseKeyMismatchException)
        assertArrayEquals(before, sha256(databaseFile))
    }

    @Test
    fun getInstance_plaintextFile_recreatesEncryptedWithoutData() {
        databaseFile.parentFile?.mkdirs()
        SQLiteDatabase.openOrCreateDatabase(databaseFile, null).use { database ->
            database.execSQL("CREATE TABLE LastBlockHeight (height INTEGER NOT NULL, id TEXT NOT NULL, PRIMARY KEY(id))")
            database.execSQL("INSERT INTO LastBlockHeight VALUES (42, '')")
        }
        assertArrayEquals(plaintextHeader, header())

        kit(keyA)

        assertNull(kitDatabase(keyA).lastBlockHeightDao().getLastBlockHeight())
        assertFalse(header().contentEquals(plaintextHeader))
    }

    @Test
    fun clear_openKit_closesDatabaseAndDeletesFiles() {
        val kit = kit(keyA)
        kit.getTransactions()

        ThorchainKit.clear(context, network, walletId)

        val access = runCatching { kit.getTransactions() }.exceptionOrNull()
        assertClosed(access)
        assertTrue(databaseFiles().toString(), databaseFiles().isEmpty())
    }

    @Test
    fun getInstance_otherKeyAfterStop_closesFirstKitAndThrowsMismatch() {
        val first = kit(keyA)
        val firstDatabase = kitDatabase(keyA).apply { lastBlockHeightDao().insert(LastBlockHeight(42)) }
        first.stop()

        val failure = runCatching { kit(keyB) }.exceptionOrNull()

        assertTrue(failure.toString(), failure is DatabaseKeyMismatchException)
        val kitAccess = runCatching { first.getTransactions() }.exceptionOrNull()
        val databaseAccess = runCatching { firstDatabase.lastBlockHeightDao().getLastBlockHeight() }.exceptionOrNull()
        assertClosed(kitAccess)
        assertClosed(databaseAccess)
        assertTrue(databaseFile.exists())
        assertEquals(42L, kitDatabase(keyA).lastBlockHeightDao().getLastBlockHeight()?.height)
    }

    @Test
    fun clear_deleteFails_doesNotKeepClosedDatabaseRegistered() {
        val first = kitDatabase(keyA)
        val failingDelete = object : ContextWrapper(context) {
            override fun deleteDatabase(name: String): Boolean = throw IllegalStateException("delete failed")
        }

        val failure = runCatching { ThorchainKit.clear(failingDelete, network, walletId) }.exceptionOrNull()
        val second = kitDatabase(keyA)

        assertTrue(failure.toString(), failure is IllegalStateException)
        assertNotSame(first, second)
        assertNull(second.lastBlockHeightDao().getLastBlockHeight())
    }

    // Android surfaces a closed database as SQLiteException (SQLITE_MISUSE) from SQLCipher, not Room's IllegalStateException.
    private fun assertClosed(failure: Throwable?) {
        val closedType = failure is SQLException || failure is IllegalStateException
        assertTrue(failure.toString(), closedType && failure?.message.orEmpty().contains("closed"))
    }
}
