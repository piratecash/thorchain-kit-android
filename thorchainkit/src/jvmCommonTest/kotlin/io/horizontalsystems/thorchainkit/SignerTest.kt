package io.horizontalsystems.thorchainkit

import fr.acinq.secp256k1.Secp256k1
import io.horizontalsystems.hdwalletkit.Mnemonic
import io.horizontalsystems.thorchainkit.network.Network
import io.horizontalsystems.thorchainkit.transaction.Signer
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SignerTest {

    // standard BIP39 test mnemonic; expected values independently derived
    // (pure-python BIP32/secp256k1/bech32) at m/44'/931'/0'/0/0
    private val mnemonic = "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon about"
    private val expectedSeed = "5eb00bbddcf069084889a8ab9155568165f5c453ccb85e70811aaed6f6da5fc19a5ac40b389cd370d086206dec8aa6c43daea6690f20ad3d8d48b2d2ce9e38e4"
    private val expectedPrivateKey = "cd48c8b23a5d619cb67b7a4886d25127acf2e8c023e42a1e9ae14c6194532aa9"
    private val expectedPublicKey = "02205c476a22d5fe10b74489db9479d0e36e25a32da393a771fcf12380136a451f"
    private val expectedAddress = "thor1gm00vwsfcp48enm4uv9e5dhm37jtd0ye27wrx0"

    private val seed = Mnemonic().toSeed(mnemonic.split(" "))

    @Test
    fun seedFromMnemonic() {
        assertEquals(expectedSeed, seed.toHexString())
    }

    @Test
    fun privateKeyDerivation() {
        val privateKey = Signer.privateKey(seed, Network.Mainnet)

        assertEquals(expectedPrivateKey, privateKey.toString(16).padStart(64, '0'))
    }

    @Test
    fun publicKeyAndAddress() {
        val privateKey = Signer.privateKey(seed, Network.Mainnet)
        val signer = Signer(privateKey)

        assertEquals(expectedPublicKey, signer.publicKey.toHexString())
        assertEquals(expectedAddress, Signer.address(privateKey, Network.Mainnet).toString())
    }

    @Test
    fun sign() {
        val signer = Signer.getInstance(seed, Network.Mainnet)
        val digest = ByteArray(32) { it.toByte() }

        val signature = signer.sign(digest)

        assertEquals(64, signature.size)
        assertTrue(Secp256k1.get().verify(signature, digest, signer.publicKey))
    }

    @Test
    fun sign_deterministic() {
        val signer = Signer.getInstance(seed, Network.Mainnet)
        val digest = ByteArray(32) { 7 }

        assertArrayEquals(signer.sign(digest), signer.sign(digest))
    }
}
