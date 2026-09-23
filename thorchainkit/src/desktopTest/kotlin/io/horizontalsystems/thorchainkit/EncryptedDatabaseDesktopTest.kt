package io.horizontalsystems.thorchainkit

import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
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
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.security.MessageDigest

class EncryptedDatabaseDesktopTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val walletId = "encryption-test"
    private val databaseName = "thorchain-Mainnet-$walletId"
    private val keyA = ByteArray(32) { it.toByte() }
    private val keyB = ByteArray(32) { (it + 100).toByte() }
    private val plaintextHeader = "SQLite format 3\u0000".encodeToByteArray()

    private val dataDir: File get() = File(tmp.root, "data")
    private val context: PlatformContext get() = PlatformContext(dataDir)
    private val databaseFile: File get() = File(dataDir, databaseName)

    private fun kitDatabase(key: ByteArray): MainDatabase =
        ThorchainDatabaseManager.getMainDatabase(context, Network.Mainnet, walletId, key)

    private fun external(key: ByteArray): MainDatabase = MainDatabase.getInstance(context, databaseName, key)

    private fun header(): ByteArray = databaseFile.readBytes().copyOf(16)

    private fun sha256(file: File): ByteArray = MessageDigest.getInstance("SHA-256").digest(file.readBytes())

    private fun hex(key: ByteArray): String = key.joinToString("") { "%02x".format(it) }

    private fun databaseFiles(): List<String> =
        dataDir.listFiles().orEmpty().map { it.name }.filter { it.startsWith(databaseName) }

    @After
    fun tearDown() {
        ThorchainKit.clear(dataDir, Network.Mainnet, walletId)
    }

    @Test
    fun getMainDatabase_newDatabase_writesEncryptedFileReadableWithSameKey() {
        kitDatabase(keyA).lastBlockHeightDao().insert(LastBlockHeight(42))

        val reopened = external(keyA).lastBlockHeightDao().getLastBlockHeight()

        assertEquals(42L, reopened?.height)
        assertFalse(header().contentEquals(plaintextHeader))
    }

    @Test
    fun getMainDatabase_wrongKey_throwsMismatchAndKeepsFileBytes() {
        external(keyA).apply {
            lastBlockHeightDao().insert(LastBlockHeight(42))
            close()
        }
        val before = sha256(databaseFile)

        val failure = runCatching { kitDatabase(keyB) }.exceptionOrNull()

        assertTrue(failure.toString(), failure is DatabaseKeyMismatchException)
        assertArrayEquals(before, sha256(databaseFile))
    }

    @Test
    fun getMainDatabase_wrongKey_messagesOmitKeyMaterial() {
        external(keyA).close()

        val failure = runCatching { kitDatabase(keyB) }.exceptionOrNull()

        val messages = generateSequence(failure) { it.cause }.mapNotNull { it.message }.toList()
        assertTrue(messages.isNotEmpty())
        messages.forEach { message ->
            assertFalse(message.contains(hex(keyA), ignoreCase = true))
            assertFalse(message.contains(hex(keyB), ignoreCase = true))
        }
        assertEquals("Database key is invalid for: ${databaseFile.absolutePath}", failure?.message)
    }

    @Test
    fun getMainDatabase_plaintextFileWithData_recreatesEncryptedWithoutData() {
        dataDir.mkdirs()
        BundledSQLiteDriver().open(databaseFile.absolutePath).use { connection ->
            connection.execSQL("CREATE TABLE LastBlockHeight (height INTEGER NOT NULL, id TEXT NOT NULL, PRIMARY KEY(id))")
            connection.execSQL("INSERT INTO LastBlockHeight VALUES (42, '')")
        }
        assertArrayEquals(plaintextHeader, header())

        val database = kitDatabase(keyA)

        assertNull(database.lastBlockHeightDao().getLastBlockHeight())
        assertFalse(header().contentEquals(plaintextHeader))
    }

    @Test
    fun clear_encryptedDatabase_deletesFiles() {
        kitDatabase(keyA).lastBlockHeightDao().insert(LastBlockHeight(42))

        ThorchainKit.clear(dataDir, Network.Mainnet, walletId)

        assertTrue(databaseFiles().toString(), databaseFiles().isEmpty())
    }

    @Test
    fun getMainDatabase_differentKeyWithOpenInstances_closesAllThenThrowsMismatch() {
        val kit = kitDatabase(keyA).apply { lastBlockHeightDao().insert(LastBlockHeight(42)) }
        val external = external(keyA).apply { lastBlockHeightDao().getLastBlockHeight() }

        val failure = runCatching { kitDatabase(keyB) }.exceptionOrNull()

        assertTrue(failure.toString(), failure is DatabaseKeyMismatchException)
        val kitAccess = runCatching { kit.lastBlockHeightDao().getLastBlockHeight() }.exceptionOrNull()
        val externalAccess = runCatching { external.lastBlockHeightDao().getLastBlockHeight() }.exceptionOrNull()
        assertTrue(kitAccess.toString(), kitAccess is IllegalStateException)
        assertTrue(externalAccess.toString(), externalAccess is IllegalStateException)
        val reopened = kitDatabase(keyA)
        assertNotSame(kit, reopened)
        assertEquals(42L, reopened.lastBlockHeightDao().getLastBlockHeight()?.height)
    }

    @Test
    fun getInstance_31ByteKey_throwsWithoutCreatingFiles() {
        val address = Address.fromString("thor1gm00vwsfcp48enm4uv9e5dhm37jtd0ye27wrx0", Network.Mainnet)

        val failure = runCatching {
            ThorchainKit.getInstance(dataDir, address, Network.Mainnet, walletId, ByteArray(31))
        }.exceptionOrNull()

        assertTrue(failure.toString(), failure is IllegalArgumentException)
        assertFalse(dataDir.exists())
    }
}
