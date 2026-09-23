package io.horizontalsystems.thorchainkit.crypto

// Bech32 (BIP-173) encoding, as used by Cosmos-SDK chains for account addresses
object Bech32 {

    private const val CHARSET = "qpzry9x8gf2tvdw0s3jn54khce6mua7l"
    private val GENERATOR = intArrayOf(0x3b6a57b2, 0x26508e6d, 0x1ea119fa, 0x3d4233dd, 0x2a1462b3)

    fun encode(hrp: String, data: ByteArray): String {
        val data5 = convertBits(data, 8, 5, pad = true)
        val checksum = createChecksum(hrp, data5)
        val combined = data5 + checksum

        return buildString {
            append(hrp)
            append('1')
            combined.forEach { append(CHARSET[it.toInt()]) }
        }
    }

    fun decode(bech: String): Pair<String, ByteArray> {
        require(bech.length in 8..90) { "Invalid bech32 string length: ${bech.length}" }
        require(bech.lowercase() == bech || bech.uppercase() == bech) { "Mixed case bech32 string" }

        val lowered = bech.lowercase()
        val pos = lowered.lastIndexOf('1')
        require(pos in 1..lowered.length - 7) { "Invalid separator position: $pos" }

        val hrp = lowered.substring(0, pos)
        hrp.forEach {
            require(it.code in 33..126) { "Invalid character in hrp: $it" }
        }

        val data5 = ByteArray(lowered.length - pos - 1)
        lowered.substring(pos + 1).forEachIndexed { index, char ->
            val value = CHARSET.indexOf(char)
            require(value >= 0) { "Invalid character in data: $char" }
            data5[index] = value.toByte()
        }

        require(verifyChecksum(hrp, data5)) { "Invalid checksum" }

        return Pair(hrp, convertBits(data5.copyOfRange(0, data5.size - 6), 5, 8, pad = false))
    }

    private fun polymod(values: ByteArray): Int {
        var chk = 1
        values.forEach { value ->
            val b = chk ushr 25
            chk = (chk and 0x1ffffff) shl 5 xor (value.toInt() and 0xff)
            for (i in 0..4) {
                if ((b ushr i) and 1 == 1) {
                    chk = chk xor GENERATOR[i]
                }
            }
        }
        return chk
    }

    private fun hrpExpand(hrp: String): ByteArray {
        val result = ByteArray(hrp.length * 2 + 1)
        hrp.forEachIndexed { index, char ->
            result[index] = (char.code ushr 5).toByte()
            result[index + hrp.length + 1] = (char.code and 31).toByte()
        }
        return result
    }

    private fun createChecksum(hrp: String, data5: ByteArray): ByteArray {
        val values = hrpExpand(hrp) + data5 + ByteArray(6)
        val polymod = polymod(values) xor 1
        return ByteArray(6) { i ->
            ((polymod ushr (5 * (5 - i))) and 31).toByte()
        }
    }

    private fun verifyChecksum(hrp: String, data5: ByteArray): Boolean {
        return polymod(hrpExpand(hrp) + data5) == 1
    }

    private fun convertBits(data: ByteArray, fromBits: Int, toBits: Int, pad: Boolean): ByteArray {
        var acc = 0
        var bits = 0
        val maxv = (1 shl toBits) - 1
        val result = mutableListOf<Byte>()

        data.forEach { byte ->
            val value = byte.toInt() and 0xff
            require(value ushr fromBits == 0) { "Invalid data range: $value" }
            acc = (acc shl fromBits) or value
            bits += fromBits
            while (bits >= toBits) {
                bits -= toBits
                result.add(((acc ushr bits) and maxv).toByte())
            }
        }

        if (pad) {
            if (bits > 0) {
                result.add(((acc shl (toBits - bits)) and maxv).toByte())
            }
        } else {
            require(bits < fromBits) { "Invalid padding length" }
            require((acc shl (toBits - bits)) and maxv == 0) { "Non-zero padding" }
        }

        return result.toByteArray()
    }
}
