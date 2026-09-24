package io.horizontalsystems.thorchainkit

import io.horizontalsystems.thorchainkit.database.MainDatabase
import io.horizontalsystems.thorchainkit.database.Storage
import io.horizontalsystems.thorchainkit.models.Transaction
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.math.BigInteger

class StorageFeeTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val database by lazy {
        MainDatabase.getInstance(PlatformContext(tmp.root), "fee-test", ByteArray(32) { it.toByte() })
    }
    private val storage by lazy { Storage(database) }

    @After
    fun tearDown() {
        database.close()
    }

    private fun transaction(hash: String, timestamp: Long = 1_000, fee: BigInteger? = null) =
        Transaction(hash, 10, timestamp, "send", "success", null, emptyList(), emptyList(), fee)

    private fun stored(hash: String): Transaction = storage.getTransactions(null, null).single { it.hash == hash }

    @Test
    fun saveFee_storedFee_readBack() {
        storage.saveTransactions(listOf(transaction("AA")))
        storage.addFeeLookups(listOf("AA"))

        storage.saveFee("AA", BigInteger("2000000"))

        assertEquals(BigInteger("2000000"), stored("AA").fee)
    }

    @Test
    fun saveTransactions_resyncedWithoutFee_keepsStoredFee() {
        storage.saveTransactions(listOf(transaction("AA")))
        storage.saveFee("AA", BigInteger("2000000"))

        storage.saveTransactions(listOf(transaction("AA").copy(status = "pending")))

        assertEquals(BigInteger("2000000"), stored("AA").fee)
        assertEquals("pending", stored("AA").status)
    }

    @Test
    fun getFeeLookups_unresolved_newestFirstUpToLimit() {
        storage.saveTransactions(listOf(transaction("OLD", 1_000), transaction("MID", 2_000), transaction("NEW", 3_000)))
        storage.addFeeLookups(listOf("OLD", "MID", "NEW"))

        assertEquals(listOf("NEW", "MID"), storage.getFeeLookups(2).map { it.hash })
    }

    @Test
    fun saveFee_withoutFee_resolvesLookupForGood() {
        storage.saveTransactions(listOf(transaction("AA")))
        storage.addFeeLookups(listOf("AA"))

        storage.saveFee("AA", null)
        storage.addFeeLookups(listOf("AA"))

        assertTrue(storage.getFeeLookups(10).isEmpty())
        assertNull(stored("AA").fee)
    }
}
