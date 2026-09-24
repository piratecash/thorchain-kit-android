package io.horizontalsystems.thorchainkit.database

import androidx.room.Room
import io.horizontalsystems.thorchainkit.PlatformContext
import io.horizontalsystems.thorchainkit.databaseKeyLiteral
import net.zetetic.database.sqlcipher.SQLiteDatabase
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import java.io.File

// A failed load is retried on the next call and fails the same way.
private val sqlCipherLoaded: Unit by lazy { System.loadLibrary("sqlcipher") }

internal actual fun mainDatabaseFile(context: PlatformContext, databaseName: String): File =
    context.getDatabasePath(databaseName)

// The factory keeps the literal for the database's lifetime: it keys every connection Room opens.
internal actual fun buildMainDatabase(context: PlatformContext, databaseName: String, databaseKey: ByteArray): MainDatabase {
    sqlCipherLoaded
    return Room.databaseBuilder(context, MainDatabase::class.java, databaseName)
        // last resort only: everything stored is a re-syncable cache (no keys)
        .fallbackToDestructiveMigration()
        .allowMainThreadQueries()
        .openHelperFactory(SupportOpenHelperFactory(databaseKeyLiteral(databaseKey)))
        .build()
}

internal actual fun verifyMainDatabaseKey(file: File, databaseKey: ByteArray) {
    sqlCipherLoaded
    val literal = databaseKeyLiteral(databaseKey)
    try {
        SQLiteDatabase.openDatabase(file.absolutePath, literal, null, SQLiteDatabase.OPEN_READONLY, null).use { database ->
            database.rawQuery("SELECT count(*) FROM sqlite_schema", emptyArray<String>()).use { cursor ->
                cursor.moveToFirst()
            }
        }
    } finally {
        literal.fill(0)
    }
}

internal actual fun deleteMainDatabase(context: PlatformContext, databaseName: String) {
    context.deleteDatabase(databaseName)
}
