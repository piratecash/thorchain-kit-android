package io.horizontalsystems.thorchainkit.sample

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.horizontalsystems.thorchainkit.ThorchainKit
import io.horizontalsystems.thorchainkit.sample.shared.SampleController
import io.horizontalsystems.thorchainkit.sample.shared.SampleUiState
import io.horizontalsystems.thorchainkit.sample.shared.SessionStore
import io.horizontalsystems.thorchainkit.sample.shared.sampleDatabaseKey
import kotlinx.coroutines.flow.StateFlow

/**
 * The controller outlives configuration changes on purpose (it lives on [viewModelScope]),
 * so a rotation does not restart a running sync or open a second kit instance.
 */
class SampleViewModel(application: Application) : AndroidViewModel(application) {

    private val sessionStore: SessionStore = SharedPreferencesSessionStore(application)

    val controller: SampleController = SampleController(
        sessionStore = sessionStore,
        kitFactory = { address, network ->
            ThorchainKit.getInstance(application, address, network, "sample", sampleDatabaseKey(sessionStore))
        },
        scope = viewModelScope
    )

    val uiState: StateFlow<SampleUiState> = controller.uiState
}

/**
 * NOTE: a sample app storing a plaintext mnemonic in SharedPreferences is fine for a demo,
 * but a real wallet must keep the mnemonic in encrypted/keystore-backed storage.
 */
private class SharedPreferencesSessionStore(context: Context) : SessionStore {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    override fun get(key: String): String? = prefs.getString(key, null)

    override fun put(key: String, value: String) {
        prefs.edit().putString(key, value).apply()
    }

    override fun remove(key: String) {
        prefs.edit().remove(key).apply()
    }

    companion object {
        private const val PREFS_NAME = "thorchainkit_sample"
    }
}
