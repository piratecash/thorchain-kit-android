package io.horizontalsystems.thorchainkit

import io.horizontalsystems.thorchainkit.network.ConnectionManager
import io.horizontalsystems.thorchainkit.sync.SyncTimer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ConnectionManagerDesktopTest {

    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun syncTimerStart_desktop_becomesReady() {
        val syncTimer = SyncTimer(15, ConnectionManager(PlatformContext(tmp.root)))
        val scope = CoroutineScope(Job())

        syncTimer.start(object : SyncTimer.Listener {
            override fun onUpdateSyncTimerState(state: SyncTimer.State) = Unit
            override fun sync() = Unit
        }, scope)

        try {
            assertEquals(SyncTimer.State.Ready, syncTimer.state)
        } finally {
            syncTimer.stop()
            scope.cancel()
        }
    }
}
