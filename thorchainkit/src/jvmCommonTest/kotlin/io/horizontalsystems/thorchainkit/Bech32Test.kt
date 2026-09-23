package io.horizontalsystems.thorchainkit

import io.horizontalsystems.thorchainkit.crypto.Bech32
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class Bech32Test {

    @Test
    fun encode_knownAddress() {
        val payload = "46def63a09c06a7ccf75e30b9a36fb8fa4b6bc99".hexToBytes()

        assertEquals("thor1gm00vwsfcp48enm4uv9e5dhm37jtd0ye27wrx0", Bech32.encode("thor", payload))
        assertEquals("sthor1gm00vwsfcp48enm4uv9e5dhm37jtd0ye78j4s3", Bech32.encode("sthor", payload))
    }

    @Test
    fun decode_knownAddress() {
        val (hrp, payload) = Bech32.decode("thor1gm00vwsfcp48enm4uv9e5dhm37jtd0ye27wrx0")

        assertEquals("thor", hrp)
        assertEquals("46def63a09c06a7ccf75e30b9a36fb8fa4b6bc99", payload.toHexString())
    }

    @Test
    fun decode_uppercase() {
        val (hrp, payload) = Bech32.decode("THOR1GM00VWSFCP48ENM4UV9E5DHM37JTD0YE27WRX0")

        assertEquals("thor", hrp)
        assertEquals("46def63a09c06a7ccf75e30b9a36fb8fa4b6bc99", payload.toHexString())
    }

    @Test
    fun roundTrip() {
        val payload = ByteArray(20) { it.toByte() }
        val encoded = Bech32.encode("thor", payload)
        val (hrp, decoded) = Bech32.decode(encoded)

        assertEquals("thor", hrp)
        assertEquals(payload.toHexString(), decoded.toHexString())
    }

    @Test
    fun decode_bip173Vectors() {
        // valid strings from the BIP-173 spec
        Bech32.decode("A12UEL5L")
        Bech32.decode("an83characterlonghumanreadablepartthatcontainsthenumber1andtheexcludedcharactersbio1tt5tgs")
        Bech32.decode("abcdef1qpzry9x8gf2tvdw0s3jn54khce6mua7lmqqqxw")
    }

    @Test
    fun decode_invalidChecksum() {
        assertThrows(IllegalArgumentException::class.java) {
            Bech32.decode("thor1gm00vwsfcp48enm4uv9e5dhm37jtd0ye27wrx1")
        }
    }

    @Test
    fun decode_mixedCase() {
        assertThrows(IllegalArgumentException::class.java) {
            Bech32.decode("Thor1gm00vwsfcp48enm4uv9e5dhm37jtd0ye27wrx0")
        }
    }

    @Test
    fun decode_invalidCharacter() {
        assertThrows(IllegalArgumentException::class.java) {
            Bech32.decode("thor1gm00vwsfcp48enm4uv9e5dhm37jtd0yb27wrx0")
        }
    }
}
