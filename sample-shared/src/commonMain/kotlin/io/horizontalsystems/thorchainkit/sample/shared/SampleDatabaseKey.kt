package io.horizontalsystems.thorchainkit.sample.shared

import java.security.SecureRandom

internal const val KEY_DATABASE_KEY: String = "database_key"

private const val DATABASE_KEY_SIZE = 32
private val HEX_KEY = Regex("[0-9a-fA-F]{${DATABASE_KEY_SIZE * 2}}")

/**
 * Returns the sample's database key, generating and persisting a random one on first use or when the
 * stored value is unusable. A demo shortcut: a real wallet keeps this key in keystore-backed storage.
 * Never log or display the returned key.
 */
fun sampleDatabaseKey(store: SessionStore): ByteArray {
    store.get(KEY_DATABASE_KEY)?.takeIf { HEX_KEY.matches(it) }?.let { return it.hexToByteArray() }

    val key = ByteArray(DATABASE_KEY_SIZE).also { SecureRandom().nextBytes(it) }
    store.put(KEY_DATABASE_KEY, key.toHexString())
    return key
}
