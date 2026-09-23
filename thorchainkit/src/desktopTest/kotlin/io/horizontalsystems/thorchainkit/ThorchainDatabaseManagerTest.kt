package io.horizontalsystems.thorchainkit

import io.horizontalsystems.thorchainkit.database.MainDatabase
import io.horizontalsystems.thorchainkit.database.ThorchainDatabaseManager
import io.horizontalsystems.thorchainkit.models.Address
import io.horizontalsystems.thorchainkit.network.Network
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.net.URL

class ThorchainDatabaseManagerTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val walletId = "registry-test"
    private val databaseName = "thorchain-Mainnet-$walletId"
    private val address = Address.fromString("thor1gm00vwsfcp48enm4uv9e5dhm37jtd0ye27wrx0", Network.Mainnet)

    // Nothing listens there: the kit must not reach live endpoints from a unit test.
    private val offline = listOf(URL("http://127.0.0.1:1/"))

    private val databaseKey = ByteArray(32) { it.toByte() }

    private fun dataDir(): File = File(tmp.root, "data")

    private fun context(dataDir: File): PlatformContext = PlatformContext(dataDir)

    private fun kitDatabase(dataDir: File): MainDatabase =
        ThorchainDatabaseManager.getMainDatabase(context(dataDir), Network.Mainnet, walletId, databaseKey)

    private fun clear(dataDir: File) = ThorchainKit.clear(dataDir, Network.Mainnet, walletId)

    private fun databaseFiles(dataDir: File): List<String> =
        dataDir.listFiles().orEmpty().map { it.name }.filter { it.startsWith(databaseName) }

    @Test
    fun getInstance_sameWalletTwice_sharesDatabase() {
        val dataDir = dataDir()
        try {
            ThorchainKit.getInstance(dataDir, address, Network.Mainnet, walletId, databaseKey, thornodeUrls = offline, midgardUrls = offline)
            val first = kitDatabase(dataDir)
            ThorchainKit.getInstance(dataDir, address, Network.Mainnet, walletId, databaseKey, thornodeUrls = offline, midgardUrls = offline)

            assertSame(first, kitDatabase(dataDir))
        } finally {
            clear(dataDir)
        }
    }

    @Test
    fun getMainDatabase_afterClear_opensNewDatabase() {
        val dataDir = dataDir()
        try {
            val before = kitDatabase(dataDir)
            clear(dataDir)

            val after = kitDatabase(dataDir)

            assertNotSame(before, after)
            assertTrue(after.transactionDao().getAll().isEmpty())
        } finally {
            clear(dataDir)
        }
    }

    @Test
    fun clear_withExternalInstance_closesItBeforeDelete() {
        val dataDir = dataDir()
        kitDatabase(dataDir)
        val external = MainDatabase.getInstance(context(dataDir), databaseName, databaseKey)
        external.transactionDao().getAll()

        clear(dataDir)

        val result = runCatching { external.transactionDao().getAll() }
        assertTrue(result.exceptionOrNull().toString(), result.exceptionOrNull() is IllegalStateException)
        assertTrue(databaseFiles(dataDir).toString(), databaseFiles(dataDir).isEmpty())
    }

    @Test
    fun getMainDatabase_openFails_leavesNoRegisteredInstance() {
        val dataDir = dataDir()
        val blocker = File(dataDir, databaseName).apply { mkdirs() }
        try {
            val failure = runCatching { kitDatabase(dataDir) }
            assertTrue(failure.isFailure)
            assertTrue(blocker.delete())

            val database = kitDatabase(dataDir)

            assertTrue(database.transactionDao().getAll().isEmpty())
            assertTrue(File(dataDir, databaseName).isFile)
        } finally {
            clear(dataDir)
        }
    }

    @Test
    fun getInstance_publicCalledTwice_returnsDifferentInstances() {
        val context = context(dataDir())

        val first = MainDatabase.getInstance(context, "external-test", databaseKey)
        val second = MainDatabase.getInstance(context, "external-test", databaseKey)

        assertNotSame(first, second)
        first.close()
        second.close()
    }
}
