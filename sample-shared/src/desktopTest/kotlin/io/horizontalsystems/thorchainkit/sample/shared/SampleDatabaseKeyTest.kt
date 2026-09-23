package io.horizontalsystems.thorchainkit.sample.shared

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class SampleDatabaseKeyTest {

    private class MapSessionStore : SessionStore {
        val values = mutableMapOf<String, String>()
        override fun get(key: String): String? = values[key]
        override fun put(key: String, value: String) {
            values[key] = value
        }
        override fun remove(key: String) {
            values.remove(key)
        }
    }

    @Test
    fun sampleDatabaseKey_emptyStore_generates32Bytes() {
        assertEquals(32, sampleDatabaseKey(MapSessionStore()).size)
    }

    @Test
    fun sampleDatabaseKey_sameStoreTwice_returnsSameKey() {
        val store = MapSessionStore()

        val first = sampleDatabaseKey(store)
        val second = sampleDatabaseKey(store)

        assertEquals(32, second.size)
        assertArrayEquals(first, second)
    }

    @Test
    fun sampleDatabaseKey_corruptedStoredValue_replacesWithValidKey() {
        val store = MapSessionStore()
        store.values[KEY_DATABASE_KEY] = "zz" + "0".repeat(62)
        val corrupted = store.values.getValue(KEY_DATABASE_KEY)

        val key = sampleDatabaseKey(store)

        assertEquals(32, key.size)
        val stored = store.values.getValue(KEY_DATABASE_KEY)
        assertNotEquals(corrupted, stored)
        assertArrayEquals(key, sampleDatabaseKey(store))
    }

    @Test
    fun sampleDatabaseKey_shortStoredValue_replacesWithValidKey() {
        val store = MapSessionStore()
        store.values[KEY_DATABASE_KEY] = "00".repeat(31)

        val key = sampleDatabaseKey(store)

        assertEquals(32, key.size)
        assertEquals(64, store.values.getValue(KEY_DATABASE_KEY).length)
    }
}
