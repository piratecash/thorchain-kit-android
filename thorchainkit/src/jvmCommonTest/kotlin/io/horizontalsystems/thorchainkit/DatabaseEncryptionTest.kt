package io.horizontalsystems.thorchainkit

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.sql.SQLException

class DatabaseEncryptionTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val encryptedHeader = ByteArray(4096) { (it * 31 + 7).toByte() }
    private val plaintextHeader = "SQLite format 3\u0000".encodeToByteArray() + ByteArray(100)

    private var verified = 0
    private var deleted = 0

    private fun prepare(file: File, verify: (File) -> Unit = { verified++ }) =
        prepareDatabaseFile(file, verifyKey = verify, deleteFiles = { deleted++ })

    private fun file(bytes: ByteArray?): File = File(tmp.root, "db").also { if (bytes != null) it.writeBytes(bytes) }

    @Test
    fun prepareDatabaseFile_missingFile_neitherVerifiesNorDeletes() {
        prepare(file(null))

        assertEquals(0, verified)
        assertEquals(0, deleted)
    }

    @Test
    fun prepareDatabaseFile_plaintextFile_deletesWithoutVerifying() {
        prepare(file(plaintextHeader))

        assertEquals(0, verified)
        assertEquals(1, deleted)
    }

    @Test
    fun prepareDatabaseFile_emptyFile_deletesWithoutVerifying() {
        prepare(file(ByteArray(0)))

        assertEquals(0, verified)
        assertEquals(1, deleted)
    }

    @Test
    fun prepareDatabaseFile_fileShorterThanHeader_deletesWithoutVerifying() {
        prepare(file(encryptedHeader.copyOf(15)))

        assertEquals(0, verified)
        assertEquals(1, deleted)
    }

    @Test
    fun prepareDatabaseFile_encryptedFileKeyMatches_verifiesWithoutDeleting() {
        prepare(file(encryptedHeader))

        assertEquals(1, verified)
        assertEquals(0, deleted)
    }

    @Test
    fun prepareDatabaseFile_encryptedFileKeyFails_throwsMismatchAndKeepsFile() {
        val file = file(encryptedHeader)
        val cause = IllegalStateException("file is not a database")

        try {
            prepare(file) { throw cause }
            fail("expected DatabaseKeyMismatchException")
        } catch (e: DatabaseKeyMismatchException) {
            assertSame(cause, e.cause)
            assertEquals(file.absolutePath, e.databasePath)
        }
        assertEquals(0, deleted)
        assertArrayEquals(encryptedHeader, file.readBytes())
    }

    @Test
    fun prepareDatabaseFile_verifyThrowsLinkageError_propagatesUnwrapped() {
        val result = runCatching { prepare(file(encryptedHeader)) { throw UnsatisfiedLinkError("no sqlcipher") } }

        assertTrue(result.exceptionOrNull().toString(), result.exceptionOrNull() is UnsatisfiedLinkError)
        assertEquals(0, deleted)
    }

    @Test
    fun prepareDatabaseFile_verifyThrowsCheckedException_throwsMismatch() {
        val result = runCatching { prepare(file(encryptedHeader)) { throw SQLException("bad key") } }

        assertTrue(result.exceptionOrNull().toString(), result.exceptionOrNull() is DatabaseKeyMismatchException)
    }

    @Test
    fun validatedDatabaseKey_31Bytes_throwsIllegalArgument() {
        val result = runCatching { validatedDatabaseKey(ByteArray(31)) }

        assertTrue(result.exceptionOrNull() is IllegalArgumentException)
    }

    @Test
    fun validatedDatabaseKey_33Bytes_throwsIllegalArgument() {
        val result = runCatching { validatedDatabaseKey(ByteArray(33)) }

        assertTrue(result.exceptionOrNull() is IllegalArgumentException)
    }

    @Test
    fun validatedDatabaseKey_32Bytes_returnsIndependentCopy() {
        val original = ByteArray(32) { it.toByte() }

        val copy = validatedDatabaseKey(original)
        original.fill(0)

        assertArrayEquals(ByteArray(32) { it.toByte() }, copy)
    }

    @Test
    fun databaseKeyLiteral_key_rawKeyHexLiteral() {
        val key = ByteArray(32) { (it * 8 + 0x0f).toByte() }
        val expected = "x'" + key.joinToString("") { "%02x".format(it) } + "'"

        val literal = databaseKeyLiteral(key)

        assertEquals(67, literal.size)
        assertEquals(expected, literal.decodeToString())
    }

    @Test
    fun withSqlCipherNative_nativeLoaderFails_throwsUnsupportedOnFirstAndRepeatedCall() {
        val first = runCatching { withSqlCipherNative { BrokenNative.open() } }.exceptionOrNull()
        val second = runCatching { withSqlCipherNative { BrokenNative.open() } }.exceptionOrNull()

        assertTrue(first.toString(), first is UnsupportedOperationException)
        assertTrue(first?.cause is ExceptionInInitializerError)
        assertTrue(second.toString(), second is UnsupportedOperationException)
        assertTrue(second?.cause is NoClassDefFoundError)
        assertEquals(first?.message, second?.message)
    }

    @Test
    fun withSqlCipherNative_otherFailure_propagatesUnchanged() {
        val failure = IllegalStateException("disk full")

        val result = runCatching { withSqlCipherNative { throw failure } }

        assertSame(failure, result.exceptionOrNull())
        assertFalse(result.isSuccess)
    }

    // Mirrors a native loader in a static initializer: the JVM reports the first failure and a
    // different LinkageError on every later access.
    private object BrokenNative {
        init {
            if (System.nanoTime() != 0L) throw UnsupportedOperationException("no native for this platform")
        }

        fun open(): Nothing = error("unreachable")
    }
}
