# ThorchainKit

Native Kotlin SDK for THORChain and Maya (a THORChain fork) — RUNE and THORChain-native
assets (TCY, RUJI, Secured Assets), and Maya's CACAO.

- HD key derivation `m/44'/931'/0'/0/0` (coin type 931 for both chains), bech32 addresses
  `thor1...` / `maya1...`
- Cosmos-style protobuf transaction signing (`SIGN_MODE_DIRECT`) with native `MsgSend` /
  `MsgDeposit`
- THORNode REST for balances, account state, fees, and broadcast
- Midgard for transaction history
- Networks: `Network.Mainnet` / `Network.Stagenet` (THORChain), `Network.MayaMainnet` (Maya)

Kotlin Multiplatform library with two JVM targets: **Android** and **desktop JVM**
(JDK 21). Forked from
[`horizontalsystems/thorchain-kit-android@568a089`](https://github.com/horizontalsystems/thorchain-kit-android)
into this piratecash fork to serve the P.CASH wallet, which is moving to Kotlin/Compose
Multiplatform.

## Denoms

The kit is denom-agnostic: balances and sends work with plain bank denom strings.
All THORChain native assets are bank denoms with **8 decimals**:

| Denom | Asset | Kind |
|---|---|---|
| `rune` | `THOR.RUNE` | native |
| `tcy` | `THOR.TCY` | native token |
| `x/ruji` | `THOR.RUJI` | native token |
| `btc-btc` | `BTC-BTC` | secured asset |
| `btc/btc` | `BTC/BTC` | synth (deprecated) |

`Denom.assetFor(denom)` / `Denom.denomFor(asset)` convert between the two notations.
Naming and metadata for these tokens live on the wallet side; the kit does not carry a
token list.

## Modules

- `thorchainkit` — the library (published)
- `thorchainkit-proto` — generated protobuf classes the library depends on (published)
- `sample-shared`, `sample-android`, `sample-desktop` — Compose Multiplatform demo app,
  never published
- `consumer-smoke` — a separate Gradle build that compiles against the artifacts
  published to `mavenLocal`, proving what a real consumer sees

## Installation

Add [JitPack](https://jitpack.io) to repositories and the dependency:

```kotlin
repositories {
    maven("https://jitpack.io")
}

dependencies {
    implementation("com.github.piratecash.thorchain-kit-android:thorchainkit:<version>")
}
```

Gradle module metadata picks the right variant (Android AAR or desktop JVM jar)
automatically; `thorchainkit-proto` (the generated protobuf classes) comes in
transitively — no separate dependency needed.

## Usage

Android factory takes a `Context`; desktop takes a data directory (`File`). Both require a
32-byte `databaseKey` — see [Database encryption](#database-encryption):

```kotlin
// Android
val kit = ThorchainKit.getInstance(context, seed, Network.Mainnet, walletId = "wallet-1", databaseKey = key)

// Desktop
val kit = ThorchainKit.getInstance(dataDir, seed, Network.Mainnet, walletId = "wallet-1", databaseKey = key)
```

Watch-only (address-only) instances are available on both platforms via the
`getInstance(context/dataDir, address, network, walletId, databaseKey)` overload; `send` /
`deposit` fail with `SignerMismatch` on those.

Offline signing: `signSend` signs without broadcasting (it still fetches the account number and
sequence) and returns `SignedTransaction(raw, hash, accountNumber, sequence)`;
`broadcastRawTransaction(raw)` relays it later. An unknown outcome is
`SendError.PossiblyAccepted(txHash)`, a consumed sequence is `SendError.SequenceConsumed`;
`transactionExists(hash)` settles either. Pass `eventListenerFactory` to `getInstance` to observe
THORNode and Midgard HTTP calls.

Call `kit.stop()` then `ThorchainKit.clear(context/dataDir, network, walletId)` to release
resources and delete local state for a wallet. On both platforms `clear` first closes every
database the kit opened for that wallet.

## Database encryption

The local cache (balances, history, block height) is always encrypted with SQLCipher; there
is no unencrypted mode. `databaseKey` is a **raw 32-byte key**, used as is — the kit applies
no key derivation. Deriving and storing it is the consumer's job (e.g. wrap it with the
Android Keystore); the kit copies the array and never logs it.

When `getInstance` opens a wallet's database file:

| File | Result |
|---|---|
| missing | created encrypted |
| plaintext or empty (e.g. written by an older kit version) | deleted and recreated encrypted; the cache resyncs from the network |
| encrypted, key matches | opened |
| encrypted with another key | `DatabaseKeyMismatchException` (a `DatabaseEncryptionException`); the file is left untouched |

To recover from a mismatch when the old key is lost, call
`ThorchainKit.clear(context/dataDir, network, walletId)` and then `getInstance` with the new
key — the cache resyncs from scratch. No funds or keys live in this database.

Other failures:

- a key that is not exactly 32 bytes → `IllegalArgumentException`, before any file or network
  object is created;
- the SQLCipher native library cannot be loaded → `UnsupportedOperationException`.

Native coverage: Android pulls `net.zetetic:sqlcipher-android` transitively; the desktop
driver ships natives for macOS arm64, Linux x64 and Windows x64.

`clear` and a `getInstance` call with a different key close every database instance the kit
opened for that wallet before touching the file. A kit of the same wallet that is still alive
afterwards — after `stop()` or running in parallel — fails on its next database access because
its database is closed (`IllegalStateException` on desktop, `android.database.SQLException` on
Android); create a new instance instead of reusing it.

## Publishing & verification

See [`PUBLISHING.md`](PUBLISHING.md) for JitPack coordinates, release tagging, and the
`consumer-smoke` check. Local verification, JDK 21 required:

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 21)

# Unit tests — both targets
./gradlew :thorchainkit:testDebugUnitTest --console=plain
./gradlew :thorchainkit:desktopTest --console=plain

# Publishable? The same task JitPack runs
./gradlew -PVERSION_NAME=0.0.0-SNAPSHOT publishToMavenLocal

# Consumer smoke: compile against the published artifacts, Android + desktop
./gradlew -p consumer-smoke compileKotlinDesktop compileDebugKotlinAndroid

# Demo app — CI may not cover it, build it yourself
./gradlew :sample-shared:compileKotlinDesktop

# Android runtime check of the encrypted database: build the instrumented test APK, then run it
# on an emulator only (never on a device that holds a wallet)
./gradlew :thorchainkit:assembleDebugAndroidTest
```

## License

MIT
