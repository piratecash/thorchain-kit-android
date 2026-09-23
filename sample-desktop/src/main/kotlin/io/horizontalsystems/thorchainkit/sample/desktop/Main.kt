package io.horizontalsystems.thorchainkit.sample.desktop

import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import io.horizontalsystems.thorchainkit.ThorchainKit
import io.horizontalsystems.thorchainkit.getInstance
import io.horizontalsystems.thorchainkit.sample.shared.SampleApp
import io.horizontalsystems.thorchainkit.sample.shared.SampleController
import io.horizontalsystems.thorchainkit.sample.shared.SessionStore
import io.horizontalsystems.thorchainkit.sample.shared.sampleDatabaseKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermissions
import java.util.Properties

fun main() {
    val home = File(System.getProperty("user.home"), ".thorchainkit-sample")
    val dataDir = File(home, "db").apply { mkdirs() }
    val sessionFile = File(home, "session.properties")

    val sessionStore = PropertiesSessionStore(sessionFile)

    val controller = SampleController(
        sessionStore = sessionStore,
        kitFactory = { address, network ->
            ThorchainKit.getInstance(dataDir, address, network, "sample", sampleDatabaseKey(sessionStore))
        },
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    )

    application {
        Window(onCloseRequest = ::exitApplication, title = "ThorchainKit Sample") {
            val state by controller.uiState.collectAsState()
            SampleApp(state, controller::onEvent)
        }
    }
}

/**
 * Backs [SessionStore] with a private properties file, the desktop equivalent of Android's
 * private SharedPreferences. Created with POSIX `rw-------` (600) permissions where supported;
 * Windows has no POSIX permission model, so creation there silently falls back to a plain file
 * (NTFS already restricts a file under the user's home directory to that user by default).
 *
 * NOTE: this stores the mnemonic in plaintext, which is fine for a demo but never for a real
 * wallet — see [SampleController]'s KDoc. The mnemonic is never logged.
 */
private class PropertiesSessionStore(private val file: File) : SessionStore {

    private val properties = Properties().apply {
        if (file.exists()) file.inputStream().use { load(it) }
    }

    override fun get(key: String): String? = properties.getProperty(key)

    override fun put(key: String, value: String) {
        properties.setProperty(key, value)
        save()
    }

    override fun remove(key: String) {
        properties.remove(key)
        save()
    }

    private fun save() {
        file.parentFile?.mkdirs()
        createPrivateIfAbsent(file)
        file.outputStream().use { properties.store(it, null) }
    }

    private fun createPrivateIfAbsent(file: File) {
        if (file.exists()) return
        try {
            val permissions = PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------"))
            Files.createFile(file.toPath(), permissions)
        } catch (_: UnsupportedOperationException) {
            // No POSIX permission model (Windows) — the stream write below creates a plain file.
        } catch (_: IOException) {
            // Best-effort: fall through to a plain create via the stream write.
        }
    }
}
