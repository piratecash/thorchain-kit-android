package io.horizontalsystems.thorchainkit.models

import io.horizontalsystems.hdwalletkit.Utils
import io.horizontalsystems.thorchainkit.crypto.Bech32
import io.horizontalsystems.thorchainkit.network.Network

class Address(
    val prefix: String,
    val payload: ByteArray
) {

    init {
        require(payload.size == 20) { "Invalid address payload size: ${payload.size}" }
    }

    override fun toString(): String {
        return Bech32.encode(prefix, payload)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Address) return false

        return prefix == other.prefix && payload.contentEquals(other.payload)
    }

    override fun hashCode(): Int {
        return 31 * prefix.hashCode() + payload.contentHashCode()
    }

    companion object {

        fun fromString(addressString: String, network: Network): Address {
            val (hrp, payload) = try {
                Bech32.decode(addressString)
            } catch (error: Throwable) {
                throw AddressValidationException("Invalid bech32 encoding: ${error.message}")
            }

            if (hrp != network.addressPrefix) {
                throw AddressValidationException("Invalid address prefix: $hrp, expected: ${network.addressPrefix}")
            }

            if (payload.size != 20) {
                throw AddressValidationException("Invalid address payload size: ${payload.size}")
            }

            return Address(hrp, payload)
        }

        fun fromPublicKey(compressedPublicKey: ByteArray, network: Network): Address {
            require(compressedPublicKey.size == 33) { "Invalid compressed public key size: ${compressedPublicKey.size}" }

            return Address(network.addressPrefix, Utils.sha256Hash160(compressedPublicKey))
        }
    }
}

class AddressValidationException(message: String) : Exception(message)
