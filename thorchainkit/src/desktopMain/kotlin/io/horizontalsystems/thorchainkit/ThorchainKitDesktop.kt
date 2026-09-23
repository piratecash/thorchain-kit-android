package io.horizontalsystems.thorchainkit

import io.horizontalsystems.thorchainkit.models.Address
import io.horizontalsystems.thorchainkit.network.Network
import java.io.File
import java.net.URL

/**
 * Desktop counterpart of the Android factory: the database lives in [dataDir]. [databaseKey] and the
 * database file follow the rules of the Android factory. The bundled SQLCipher native library covers
 * macOS arm64, Linux x64 and Windows x64; elsewhere the call fails with [UnsupportedOperationException].
 */
public fun ThorchainKit.Companion.getInstance(
    dataDir: File,
    seed: ByteArray,
    network: Network,
    walletId: String,
    databaseKey: ByteArray,
    syncInterval: Long = 15,
    thornodeUrls: List<URL> = network.thornodeUrls,
    midgardUrls: List<URL> = network.midgardUrls
): ThorchainKit = getInstance(PlatformContext(dataDir), seed, network, walletId, databaseKey, syncInterval, thornodeUrls, midgardUrls)

/**
 * Desktop watch-account factory: address only; send/deposit fail with SignerMismatch. Supported
 * platforms and [databaseKey] rules as for the seed overload.
 */
public fun ThorchainKit.Companion.getInstance(
    dataDir: File,
    address: Address,
    network: Network,
    walletId: String,
    databaseKey: ByteArray,
    syncInterval: Long = 15,
    thornodeUrls: List<URL> = network.thornodeUrls,
    midgardUrls: List<URL> = network.midgardUrls
): ThorchainKit = getInstance(PlatformContext(dataDir), address, network, walletId, databaseKey, syncInterval, thornodeUrls, midgardUrls)

/**
 * Call after [ThorchainKit.stop], as on Android: it closes every database the kit opened for this
 * wallet, so a kit still running afterwards fails on a closed database, then deletes the files.
 */
public fun ThorchainKit.Companion.clear(dataDir: File, network: Network, walletId: String): Unit =
    clear(PlatformContext(dataDir), network, walletId)
