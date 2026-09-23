package io.horizontalsystems.thorchainkit

fun String.hexToBytes(): ByteArray {
    return chunked(2).map { it.toInt(16).toByte() }.toByteArray()
}

fun ByteArray.toHexString(): String {
    return joinToString("") { "%02x".format(it) }
}
