package io.horizontalsystems.thorchainkit.transaction

import fr.acinq.secp256k1.Secp256k1
import io.horizontalsystems.hdwalletkit.HDWallet
import io.horizontalsystems.thorchainkit.models.Address
import io.horizontalsystems.thorchainkit.network.Network
import java.math.BigInteger

class Signer(
    private val privateKey: BigInteger
) {

    val publicKey: ByteArray = compressedPublicKey(privateKey)

    // Signs a 32-byte digest, returns a 64-byte r||s signature (RFC6979 deterministic, low-S),
    // the format Cosmos SIGN_MODE_DIRECT expects
    fun sign(digest: ByteArray): ByteArray {
        require(digest.size == 32) { "Invalid digest size: ${digest.size}" }

        return Secp256k1.get().sign(digest, bytes32(privateKey))
    }

    companion object {
        fun getInstance(seed: ByteArray, network: Network): Signer {
            return Signer(privateKey(seed, network))
        }

        fun privateKey(seed: ByteArray, network: Network): BigInteger {
            val hdWallet = HDWallet(seed, network.coinType, HDWallet.Purpose.BIP44)
            return BigInteger(1, hdWallet.privateKey(0, 0, true).privKeyBytes)
        }

        fun address(privateKey: BigInteger, network: Network): Address {
            val publicKey = compressedPublicKey(privateKey)
            return Address.fromPublicKey(publicKey, network)
        }

        private fun compressedPublicKey(privateKey: BigInteger): ByteArray {
            val secp256k1 = Secp256k1.get()
            return secp256k1.pubKeyCompress(secp256k1.pubkeyCreate(bytes32(privateKey)))
        }

        // Unsigned big-endian, left-padded to 32 bytes (upstream Utils.bigIntegerToBytes(k, 32))
        private fun bytes32(value: BigInteger): ByteArray {
            val bytes = value.toByteArray()
            val start = if (bytes[0] == 0.toByte()) 1 else 0
            val length = bytes.size - start
            require(length <= 32) { "Private key does not fit in 32 bytes" }
            return ByteArray(32).also { bytes.copyInto(it, destinationOffset = 32 - length, startIndex = start) }
        }
    }
}
