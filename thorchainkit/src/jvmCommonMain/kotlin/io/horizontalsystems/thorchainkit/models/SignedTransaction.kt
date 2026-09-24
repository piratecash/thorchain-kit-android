package io.horizontalsystems.thorchainkit.models

public class SignedTransaction(
    public val raw: ByteArray,
    // Cosmos tx hash: uppercase hex SHA-256 of raw
    public val hash: String,
    public val accountNumber: Long,
    public val sequence: Long
)
