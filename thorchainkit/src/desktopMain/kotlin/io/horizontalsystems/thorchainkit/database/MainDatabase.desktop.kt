package io.horizontalsystems.thorchainkit.database

import androidx.room.Room
import io.horizontalsystems.sqlcipher.SqlCipherDriver
import io.horizontalsystems.thorchainkit.PlatformContext
import kotlinx.coroutines.Dispatchers
import java.io.File

internal actual fun mainDatabaseFile(context: PlatformContext, databaseName: String): File =
    File(context.dataDir, databaseName)

// The driver keeps its own copy of the key for the database's lifetime: it keys every connection.
internal actual fun buildMainDatabase(context: PlatformContext, databaseName: String, databaseKey: ByteArray): MainDatabase {
    context.dataDir.mkdirs()
    return Room.databaseBuilder<MainDatabase>(mainDatabaseFile(context, databaseName).absolutePath)
        .setDriver(SqlCipherDriver(databaseKey))
        // A blocking DAO nested in a transaction must reach Room's `useConnection` undispatched, before
        // its first suspension, so Room recovers the transaction's connection from its thread local.
        .setQueryCoroutineContext(Dispatchers.Unconfined)
        .addMigrations(*MainDatabase.MIGRATIONS)
        // last resort only: everything stored is a re-syncable cache (no keys)
        .fallbackToDestructiveMigration(dropAllTables = false)
        .build()
}

internal actual fun verifyMainDatabaseKey(file: File, databaseKey: ByteArray) {
    SqlCipherDriver(databaseKey).use { driver ->
        driver.open(file.absolutePath).use { connection ->
            connection.prepare("SELECT count(*) FROM sqlite_schema").use { it.step() }
        }
    }
}

// Callers close every open instance first: an open connection keeps the file locked on Windows.
internal actual fun deleteMainDatabase(context: PlatformContext, databaseName: String) {
    val file = mainDatabaseFile(context, databaseName)
    listOf("", "-wal", "-shm", "-journal", ".lck").forEach { suffix ->
        File(file.path + suffix).delete()
    }
}
