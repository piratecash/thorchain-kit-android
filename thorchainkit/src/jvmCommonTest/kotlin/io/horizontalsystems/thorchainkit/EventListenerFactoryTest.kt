package io.horizontalsystems.thorchainkit

import io.horizontalsystems.thorchainkit.network.MidgardProvider
import io.horizontalsystems.thorchainkit.network.ThornodeApiProvider
import kotlinx.coroutines.runBlocking
import okhttp3.Call
import okhttp3.EventListener
import org.junit.Assert.assertEquals
import org.junit.Test
import java.net.URL
import java.util.concurrent.CopyOnWriteArrayList

class RecordingEventListenerFactory : EventListener.Factory {
    val startedUrls = CopyOnWriteArrayList<String>()

    override fun create(call: Call): EventListener = object : EventListener() {
        override fun callStart(call: Call) {
            startedUrls.add(call.request().url.toString())
        }
    }
}

class EventListenerFactoryTest {

    // Nothing listens there: the call fails at connect, after callStart.
    private val offline = URL("http://127.0.0.1:1/")

    @Test
    fun thornodeApiProviderCreate_eventListenerFactory_receivesCallEvents() {
        val factory = RecordingEventListenerFactory()
        val provider = ThornodeApiProvider.create(listOf(offline), "thorchain", factory)

        runCatching { runBlocking { provider.fetchNativeTxFee() } }

        assertEquals(listOf("${offline}thorchain/constants"), factory.startedUrls)
    }

    @Test
    fun midgardProviderCreate_eventListenerFactory_receivesCallEvents() {
        val factory = RecordingEventListenerFactory()
        val provider = MidgardProvider.create(listOf(offline), factory)

        runCatching { runBlocking { provider.fetchActions("thor1") } }

        assertEquals(1, factory.startedUrls.size)
        assertEquals("/v2/actions", URL(factory.startedUrls.single()).path)
    }
}
