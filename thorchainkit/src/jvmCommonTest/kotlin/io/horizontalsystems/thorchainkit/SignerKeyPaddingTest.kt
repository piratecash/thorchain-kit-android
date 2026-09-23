package io.horizontalsystems.thorchainkit

import io.horizontalsystems.thorchainkit.transaction.Signer
import org.junit.Assert.assertEquals
import org.junit.Test
import java.math.BigInteger

// The mnemonic vectors all have a full 32-byte key, so they never reach the left-padding branch.
class SignerKeyPaddingTest {

    @Test
    fun publicKey_privateKeyOne_isGeneratorPoint() {
        val signer = Signer(BigInteger.ONE)

        assertEquals(
            "0279be667ef9dcbbac55a06295ce870b07029bfcdb2dce28d959f2815b16f81798",
            signer.publicKey.toHexString()
        )
    }
}
