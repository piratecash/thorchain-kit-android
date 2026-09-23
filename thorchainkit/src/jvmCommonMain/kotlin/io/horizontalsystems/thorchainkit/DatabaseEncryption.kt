package io.horizontalsystems.thorchainkit

import java.io.DataInputStream
import java.io.File
import java.security.MessageDigest

/** Base type for failures to open the kit's encrypted database. Messages carry the database path only. */
public abstract class DatabaseEncryptionException(message: String, cause: Throwable? = null) :
    IllegalStateException(message, cause)

/**
 * The database at [databasePath] is encrypted with a different key. The file is left untouched;
 * the cached data can be dropped with `ThorchainKit.clear` and resynced under the current key.
 */
public class DatabaseKeyMismatchException(public val databasePath: String, cause: Throwable) :
    DatabaseEncryptionException("Database key is invalid for: $databasePath", cause)

internal const val DATABASE_KEY_SIZE: Int = 32

internal fun validatedDatabaseKey(rawKey: ByteArray): ByteArray {
    val key = rawKey.copyOf()
    if (key.size == DATABASE_KEY_SIZE) return key
    key.fill(0)
    throw IllegalArgumentException("Database key must contain exactly $DATABASE_KEY_SIZE bytes")
}

internal fun databaseKeyFingerprint(key: ByteArray): ByteArray = MessageDigest.getInstance("SHA-256").digest(key)

// SQLCipher raw-key form x'<64 hex>': the key is used as is, without PBKDF2, identical on both platforms.
internal fun databaseKeyLiteral(rawKey: ByteArray): ByteArray {
    val key = validatedDatabaseKey(rawKey)
    val hex = "0123456789abcdef".encodeToByteArray()
    return ByteArray(3 + DATABASE_KEY_SIZE * 2).also { literal ->
        literal[0] = 'x'.code.toByte()
        literal[1] = '\''.code.toByte()
        key.forEachIndexed { index, byte ->
            val value = byte.toInt() and 0xff
            literal[2 + index * 2] = hex[value ushr 4]
            literal[3 + index * 2] = hex[value and 0x0f]
        }
        literal[literal.size - 1] = '\''.code.toByte()
        key.fill(0)
    }
}

/**
 * The single rule set for an existing database file: a missing file is created by the caller, a
 * plaintext or truncated one is a disposable cache and is deleted, an encrypted one must open
 * with the key or the call fails without touching it.
 */
internal fun prepareDatabaseFile(file: File, verifyKey: (File) -> Unit, deleteFiles: () -> Unit) {
    if (!file.isFile) return
    if (isPlaintextDatabase(file)) {
        deleteFiles()
        return
    }
    try {
        verifyKey(file)
    } catch (e: Exception) {
        throw DatabaseKeyMismatchException(file.absolutePath, e)
    }
}

// Shorter than the header means no page was ever written, so there is nothing encrypted to keep.
private fun isPlaintextDatabase(file: File): Boolean {
    if (file.length() < SQLITE_HEADER.size) return true
    val header = ByteArray(SQLITE_HEADER.size)
    DataInputStream(file.inputStream()).use { it.readFully(header) }
    return header.contentEquals(SQLITE_HEADER)
}

private val SQLITE_HEADER = "SQLite format 3\u0000".encodeToByteArray()

// The native loaders fail with a LinkageError: the first access and every retry must read the same.
internal inline fun <T> withSqlCipherNative(block: () -> T): T =
    try {
        block()
    } catch (e: LinkageError) {
        throw UnsupportedOperationException("SQLCipher native library is unavailable on this platform", e)
    }
