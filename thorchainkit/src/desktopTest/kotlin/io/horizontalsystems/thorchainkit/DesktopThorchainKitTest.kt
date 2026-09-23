package io.horizontalsystems.thorchainkit

import io.horizontalsystems.thorchainkit.models.Address
import io.horizontalsystems.thorchainkit.network.Network
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.net.URL

class DesktopThorchainKitTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val walletId = "desktop-test"
    private val databaseName = "thorchain-Mainnet-$walletId"
    private val address = Address.fromString("thor1gm00vwsfcp48enm4uv9e5dhm37jtd0ye27wrx0", Network.Mainnet)

    // Nothing listens there: the kit must not reach live endpoints from a unit test.
    private val offline = listOf(URL("http://127.0.0.1:1/"))

    private val databaseKey = ByteArray(32) { it.toByte() }

    private fun dataDir(): File = File(tmp.root, "data")

    private fun watchKit(dataDir: File): ThorchainKit =
        ThorchainKit.getInstance(dataDir, address, Network.Mainnet, walletId, databaseKey, thornodeUrls = offline, midgardUrls = offline)

    @Test
    fun getInstance_watchAddress_createsDatabaseInDataDir() {
        val dataDir = dataDir()

        val kit = watchKit(dataDir)

        assertTrue(kit.getTransactions().isEmpty())
        assertEquals(address.toString(), kit.receiveAddress)
        assertTrue(File(dataDir, databaseName).isFile)
    }

    @Test
    fun getInstance_prefixMismatch_throwsWithoutCreatingDatabase() {
        val dataDir = dataDir()

        val result = runCatching {
            ThorchainKit.getInstance(dataDir, address, Network.MayaMainnet, walletId, databaseKey)
        }

        assertTrue(result.exceptionOrNull() is IllegalArgumentException)
        assertTrue(dataDir.listFiles().orEmpty().isEmpty())
    }

    @Test
    fun clear_databaseOpenedByKit_removesFiles() {
        val dataDir = dataDir()
        val kit = watchKit(dataDir)
        kit.getTransactions()
        kit.start()
        kit.stop()

        ThorchainKit.clear(dataDir, Network.Mainnet, walletId)

        val left = dataDir.listFiles().orEmpty().filter { it.name.startsWith(databaseName) }
        assertFalse(left.map { it.name }.toString(), left.isNotEmpty())
    }
}
