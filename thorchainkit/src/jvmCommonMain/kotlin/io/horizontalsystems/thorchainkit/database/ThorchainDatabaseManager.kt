package io.horizontalsystems.thorchainkit.database

import androidx.room.useWriterConnection
import io.horizontalsystems.thorchainkit.PlatformContext
import io.horizontalsystems.thorchainkit.databaseKeyFingerprint
import io.horizontalsystems.thorchainkit.network.Network
import io.horizontalsystems.thorchainkit.prepareDatabaseFile
import io.horizontalsystems.thorchainkit.validatedDatabaseKey
import io.horizontalsystems.thorchainkit.withSqlCipherNative
import kotlinx.coroutines.runBlocking
import java.util.concurrent.ConcurrentHashMap

internal object ThorchainDatabaseManager {

    // Every instance opened for a database file, so the file is never deleted under an open handle.
    // Strong references on purpose: Room does not close a native connection on GC.
    private class Registration(
        val kitDatabase: MainDatabase?,
        val externalDatabases: List<MainDatabase>,
        // SHA-256 of the kit instance's key: a different key must not be served the open instance.
        val keyFingerprint: ByteArray?,
    ) {
        val all: List<MainDatabase> get() = listOfNotNull(kitDatabase) + externalDatabases
    }

    // What is still registered while a transform runs; it survives a failed transform as is.
    private class Slot(var registration: Registration?) {
        fun closeAll() {
            registration?.all?.forEach { it.close() }
            registration = null
        }
    }

    private val registrations = ConcurrentHashMap<String, Registration>()

    fun getMainDatabase(context: PlatformContext, network: Network, walletId: String, databaseKey: ByteArray): MainDatabase {
        val databaseName = getDatabaseName(network, walletId)
        val registration = withValidatedKey(databaseKey) { key ->
            val fingerprint = databaseKeyFingerprint(key)
            update(context, databaseName) { slot ->
                val current = slot.registration
                if (current?.kitDatabase != null) {
                    if (current.keyFingerprint contentEquals fingerprint) return@update current
                    slot.closeAll()
                }
                val database = openMainDatabase(context, databaseName, key, slot)
                Registration(database, slot.registration?.externalDatabases.orEmpty(), fingerprint)
            }
        }
        return checkNotNull(registration?.kitDatabase)
    }

    // A new instance per call: callers such as migration tests close and reopen the file.
    fun openExternal(context: PlatformContext, databaseName: String, databaseKey: ByteArray): MainDatabase {
        val registration = withValidatedKey(databaseKey) { key ->
            update(context, databaseName) { slot ->
                val database = openMainDatabase(context, databaseName, key, slot)
                val current = slot.registration
                Registration(current?.kitDatabase, current?.externalDatabases.orEmpty() + database, current?.keyFingerprint)
            }
        }
        return checkNotNull(registration).externalDatabases.last()
    }

    fun clear(context: PlatformContext, network: Network, walletId: String) {
        val databaseName = getDatabaseName(network, walletId)
        update(context, databaseName) { slot ->
            slot.closeAll()
            deleteMainDatabase(context, databaseName)
            null
        }
    }

    private fun <T> withValidatedKey(databaseKey: ByteArray, block: (ByteArray) -> T): T {
        val key = validatedDatabaseKey(databaseKey)
        try {
            return block(key)
        } finally {
            key.fill(0)
        }
    }

    // A failed transform keeps whatever it left in the slot; the failure is rethrown outside the map's lock.
    private fun update(
        context: PlatformContext,
        databaseName: String,
        transform: (Slot) -> Registration?,
    ): Registration? {
        var failure: Throwable? = null
        val registration = registrations.compute(registryKey(context, databaseName)) { _, current ->
            val slot = Slot(current)
            try {
                transform(slot)
            } catch (e: Throwable) {
                failure = e
                slot.registration
            }
        }
        failure?.let { throw it }
        return registration
    }

    private fun registryKey(context: PlatformContext, databaseName: String): String =
        mainDatabaseFile(context, databaseName).absolutePath

    // Room opens lazily; opening here surfaces a wrong key, a broken file or a missing native
    // library before the instance is registered.
    private fun openMainDatabase(context: PlatformContext, databaseName: String, key: ByteArray, slot: Slot): MainDatabase =
        withSqlCipherNative {
            prepareDatabaseFile(
                mainDatabaseFile(context, databaseName),
                verifyKey = { file -> verifyMainDatabaseKey(file, key) },
                deleteFiles = {
                    slot.closeAll()
                    deleteMainDatabase(context, databaseName)
                },
            )
            val database = buildMainDatabase(context, databaseName, key)
            try {
                runBlocking { database.useWriterConnection { } }
            } catch (e: Throwable) {
                database.close()
                throw e
            }
            database
        }

    private fun getDatabaseName(network: Network, walletId: String): String {
        // protocolPath makes chain identity an explicit component, so a THORChain and a
        // Maya wallet derived from the same seed can never share a Room DB file. (network.name
        // is already unique within the single Network enum, but keying on the chain
        // explicitly is correct-by-construction rather than relying on that convention.)
        return "${network.protocolPath}-${network.name}-$walletId"
    }
}
