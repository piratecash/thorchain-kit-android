# Publishing & wallet integration

The kit publishes via [JitPack](https://jitpack.io), from a numeric SemVer tag
(`1.0.0`, `1.0.1`, ...), like our other piratecash `*-kit-android` KMP forks
(`hd-wallet-kit-android`). `jitpack.yml` pins `jdk: openjdk21` and runs

```
./gradlew -PVERSION_NAME=$VERSION publishToMavenLocal
```

which publishes both `thorchainkit` and `thorchainkit-proto` under group
`com.github.piratecash.thorchain-kit-android`:

```
com.github.piratecash.thorchain-kit-android:thorchainkit:<version>
com.github.piratecash.thorchain-kit-android:thorchainkit-proto:<version>
```

`thorchainkit` is Kotlin Multiplatform (Android + desktop JVM); Gradle module metadata
picks the right variant for the consumer's target automatically. `thorchainkit-proto` is a
plain JVM library shared by both variants and a transitive dependency of `thorchainkit`,
so consumers only declare one coordinate.
The published POM and `.module` file list all runtime dependencies (`secp256k1-kmp-jni-*`,
Room, Retrofit, `hd-wallet-kit-kmp`, SQLCipher — `net.zetetic:sqlcipher-android` on Android,
the piratecash `sqlcipher-driver` on desktop, ...) — consumers get them transitively.

## Versioning

Tags are exact numeric SemVer (`MAJOR.MINOR.PATCH`, no `v` prefix), as in `hd-wallet-kit-android`;
CI treats only such tags as releases. A fix is a patch bump (`1.0.0` → `1.0.1`), a backward-compatible
addition a minor bump, and any change a consumer has to adapt to a **major** bump.

The first release of this fork is `1.0.0`: neither this repository nor upstream
(`horizontalsystems/thorchain-kit-android`) had tags before it. `1.0.0` already carries the
Kotlin Multiplatform build and mandatory database encryption (`databaseKey: ByteArray` in every
`ThorchainKit.getInstance(...)`), so there is no earlier published API to stay compatible with.
See "Database encryption" in [`README.md`](README.md) for the key and file rules.

## CI (GitHub Actions)

`.github/workflows/ci.yml` runs on pushes to `main` and `feature/**`, on pull requests and on
release tags:

- `android` — `:thorchainkit:testDebugUnitTest`;
- `desktop` — `:thorchainkit:desktopTest` on Linux x64, Windows x64 and macOS arm64 (the platforms
  the SQLCipher desktop natives ship for);
- `publish` — `publishToMavenLocal` (the JitPack command) plus the `consumer-smoke` build;
- `sample` — the sample modules;
- `jitpack` — **release tags only**, after all of the above passed: asks JitPack to build the tag
  and waits until the `thorchainkit` module metadata, the Android AAR, the desktop jar and the
  proto jar are downloadable; if they never appear it fails and prints the JitPack build log.

CI never publishes anything itself — JitPack builds the tag from the repository. The Android
instrumented tests (`EncryptedDatabaseAndroidTest`) are not in CI: run them on an emulator
(see README).

## JitPack pitfalls

These have already cost time on our other kits — check them before touching
`jitpack.yml`:

- no `exit` inside `install:`, and no `set -eu` inside `before_install:` unless wrapped
  in a subshell `( … )` — either breaks JitPack's own build wrapper: the JitPack log looks
  green but no artifact is produced;
- **a tag is built once and never rebuilt.** A fix needs a new tag, not a re-push of the
  same one;
- the build has a **20-minute** limit.

## Publish steps

1. Run the local verification below and push the commit to `main`; wait for CI to be green.
2. Optional dry run that does not spend a version: request a build of the commit itself,
   `https://jitpack.io/com/github/piratecash/thorchain-kit-android/<commit-sha>/build.log`,
   and check that it ends successfully. A failed tag cannot be rebuilt, a failed commit build costs
   nothing.
3. Tag the commit and push the tag: `git tag 1.0.0 && git push origin 1.0.0`.
4. CI runs again on the tag; its `jitpack` job triggers the JitPack build and fails unless the
   artifacts become downloadable. The log is also at
   `https://jitpack.io/#piratecash/thorchain-kit-android/1.0.0`.
5. Bump `thorchainKit` in the wallet (below).

## Local verification before tagging

Everything JitPack runs, plus a live consumer check — see the "Publishing &
verification" section of [`README.md`](README.md) for the exact commands
(JDK 21 required). `consumer-smoke/` is a separate Gradle build (its own
`settings.gradle.kts`) that resolves `com.github.piratecash.thorchain-kit-android:thorchainkit`
from `mavenLocal` (restricted to that group only, so a stale cache can't mask a broken
publish) plus `google()` / `mavenCentral()` / JitPack for its other dependencies, and
compiles Android + desktop source sets against it — proving what a real consumer's build
sees, including that the proto classes resolve transitively.

## Wallet-side wiring (`pcash-wallet-android`)

`gradle/libs.versions.toml`:

```toml
[versions]
thorchainKit = "1.0.0"

[libraries]
thorchain-kit = { module = "com.github.piratecash.thorchain-kit-android:thorchainkit", version.ref = "thorchainKit" }
```

Then add `implementation(libs.thorchain.kit)` (or `api`, matching how the other kits are
wired) to the consuming module — `shared` for the KMP wallet code, plus any
Android-specific module that needs the Android factory directly.

The wallet must pass a 32-byte `databaseKey` to `ThorchainKit.getInstance(...)`, kept in
Keystore-backed storage, and handle `DatabaseKeyMismatchException` (recovery: `ThorchainKit.clear`
then `getInstance`). An existing plaintext database is dropped and resynced on first open.
