package io.horizontalsystems.thorchainkit

import io.horizontalsystems.thorchainkit.models.Address
import io.horizontalsystems.thorchainkit.models.AddressValidationException
import io.horizontalsystems.thorchainkit.network.Network
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class AddressTest {

    // vector independently derived (pure-python BIP32/secp256k1/bech32) from the standard
    // BIP39 mnemonic "abandon abandon ... about" at m/44'/931'/0'/0/0
    private val compressedPublicKey = "02205c476a22d5fe10b74489db9479d0e36e25a32da393a771fcf12380136a451f".hexToBytes()
    private val mainnetAddress = "thor1gm00vwsfcp48enm4uv9e5dhm37jtd0ye27wrx0"
    private val stagenetAddress = "sthor1gm00vwsfcp48enm4uv9e5dhm37jtd0ye78j4s3"
    // Maya reuses SLIP-44 coin type 931, so the same key yields the same payload as THORChain;
    // only the "maya" bech32 HRP differs. Vector independently derived (pure-python bech32).
    private val mayaAddress = "maya1gm00vwsfcp48enm4uv9e5dhm37jtd0ye2fs0sl"

    @Test
    fun fromPublicKey() {
        assertEquals(mainnetAddress, Address.fromPublicKey(compressedPublicKey, Network.Mainnet).toString())
        assertEquals(stagenetAddress, Address.fromPublicKey(compressedPublicKey, Network.Stagenet).toString())
        assertEquals(mayaAddress, Address.fromPublicKey(compressedPublicKey, Network.MayaMainnet).toString())
    }

    @Test
    fun fromString_maya() {
        val address = Address.fromString(mayaAddress, Network.MayaMainnet)

        assertEquals("maya", address.prefix)
        assertEquals(mayaAddress, address.toString())
    }

    @Test
    fun fromString_mayaWrongNetwork() {
        // a maya address must not validate as THORChain, and vice versa
        assertThrows(AddressValidationException::class.java) {
            Address.fromString(mayaAddress, Network.Mainnet)
        }
        assertThrows(AddressValidationException::class.java) {
            Address.fromString(mainnetAddress, Network.MayaMainnet)
        }
    }

    @Test
    fun fromString() {
        val address = Address.fromString(mainnetAddress, Network.Mainnet)

        assertEquals("thor", address.prefix)
        assertEquals(mainnetAddress, address.toString())
    }

    @Test
    fun fromString_wrongNetwork() {
        assertThrows(AddressValidationException::class.java) {
            Address.fromString(stagenetAddress, Network.Mainnet)
        }
        assertThrows(AddressValidationException::class.java) {
            Address.fromString(mainnetAddress, Network.Stagenet)
        }
    }

    @Test
    fun fromString_invalidChecksum() {
        assertThrows(AddressValidationException::class.java) {
            Address.fromString("thor1gm00vwsfcp48enm4uv9e5dhm37jtd0ye27wrx1", Network.Mainnet)
        }
    }

    @Test
    fun fromString_notBech32() {
        assertThrows(AddressValidationException::class.java) {
            Address.fromString("0x052ce41952bf6e8ff229d5e589932a56d4ac04ba", Network.Mainnet)
        }
    }

    @Test
    fun equality() {
        val a = Address.fromString(mainnetAddress, Network.Mainnet)
        val b = Address.fromPublicKey(compressedPublicKey, Network.Mainnet)

        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
    }
}
