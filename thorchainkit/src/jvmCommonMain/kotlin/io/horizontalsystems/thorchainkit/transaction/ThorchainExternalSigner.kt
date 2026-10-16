package io.horizontalsystems.thorchainkit.transaction

/**
 * A hardware signer such as the THORChain Ledger app: it shows the transaction parsed from a
 * SIGN_MODE_LEGACY_AMINO_JSON sign doc and signs the doc's SHA-256.
 */
public interface ThorchainExternalSigner {
    /** The 33-byte compressed secp256k1 public key the kit's address derives from. */
    public val publicKey: ByteArray

    /** Returns the 64-byte low-S r||s signature of SHA-256([signDoc]). */
    public suspend fun signAmino(signDoc: ByteArray): ByteArray
}
