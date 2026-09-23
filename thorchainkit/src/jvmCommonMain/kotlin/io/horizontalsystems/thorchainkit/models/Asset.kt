package io.horizontalsystems.thorchainkit.models

// THORChain asset notation: THOR.RUNE (native), BTC/BTC (synth), BTC~BTC (trade), BTC-BTC (secured)
data class Asset(
    val chain: String,
    val symbol: String,
    val ticker: String,
    val synth: Boolean = false,
    val trade: Boolean = false,
    val secured: Boolean = false
) {

    override fun toString(): String {
        val delimiter = when {
            synth -> '/'
            trade -> '~'
            secured -> '-'
            else -> '.'
        }
        return "$chain$delimiter$symbol"
    }

    companion object {
        val Rune = Asset("THOR", "RUNE", "RUNE")

        fun fromString(value: String): Asset {
            val index = value.indexOfFirst { it == '.' || it == '/' || it == '~' || it == '-' }
            require(index in 1 until value.length - 1) { "Invalid asset notation: $value" }

            val chain = value.substring(0, index)
            val delimiter = value[index]
            val symbol = value.substring(index + 1)

            return Asset(
                chain = chain.uppercase(),
                symbol = symbol.uppercase(),
                ticker = symbol.substringBefore('-').uppercase(),
                synth = delimiter == '/',
                trade = delimiter == '~',
                secured = delimiter == '-'
            )
        }
    }
}
