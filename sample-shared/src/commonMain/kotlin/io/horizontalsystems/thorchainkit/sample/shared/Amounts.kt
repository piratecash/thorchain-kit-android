package io.horizontalsystems.thorchainkit.sample.shared

import java.math.BigDecimal
import java.math.BigInteger

/** Formats a native-denom base-unit amount for display, e.g. RUNE (8 decimals) or CACAO (10 decimals). */
fun BigInteger.toDisplay(decimals: Int): String =
    BigDecimal(this).movePointLeft(decimals).stripTrailingZeros().toPlainString()

/** Parses a plain decimal amount typed by the user into the chain's base units. */
fun String.parseAmount(decimals: Int): BigInteger =
    BigDecimal(this).movePointRight(decimals).toBigInteger()
