package io.horizontalsystems.thorchainkit.sample.shared

import org.junit.Assert.assertEquals
import org.junit.Test
import java.math.BigInteger

class AmountsTest {

    @Test
    fun toDisplay_eightDecimals_formatsRune() {
        assertEquals("1", BigInteger("100000000").toDisplay(8))
        assertEquals("1.5", BigInteger("150000000").toDisplay(8))
        assertEquals("0.00000001", BigInteger.ONE.toDisplay(8))
        assertEquals("0", BigInteger.ZERO.toDisplay(8))
    }

    @Test
    fun toDisplay_tenDecimals_formatsCacao() {
        assertEquals("1", BigInteger("10000000000").toDisplay(10))
        assertEquals("0.0000000001", BigInteger.ONE.toDisplay(10))
        assertEquals("123.4567891234", BigInteger("1234567891234").toDisplay(10))
    }

    @Test
    fun parseAmount_eightDecimals_parsesRune() {
        assertEquals(BigInteger("100000000"), "1".parseAmount(8))
        assertEquals(BigInteger("150000000"), "1.5".parseAmount(8))
    }

    @Test
    fun parseAmount_tenDecimals_parsesCacao() {
        assertEquals(BigInteger("10000000000"), "1".parseAmount(10))
        assertEquals(BigInteger("1234567891234"), "123.4567891234".parseAmount(10))
    }
}
