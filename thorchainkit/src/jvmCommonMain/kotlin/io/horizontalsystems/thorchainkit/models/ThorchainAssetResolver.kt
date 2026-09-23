package io.horizontalsystems.thorchainkit.models

// THORChain bank denom notation. All native assets are bank denoms with 8 decimals:
// "rune", "tcy", "x/ruji" (THOR-native tokens), "btc-btc" (secured), "btc/btc" (synth)
object ThorchainAssetResolver : AssetResolver {

    override val nativeDenom = "rune"
    override val decimals = 8

    // THOR-native denoms whose notation is irregular (not derivable from the asset)
    private val nativeAssets = mapOf(
        nativeDenom to Asset.Rune,
        "tcy" to Asset("THOR", "TCY", "TCY"),
        "x/ruji" to Asset("THOR", "RUJI", "RUJI")
    )

    override fun assetFor(denom: String): Asset {
        val lowered = denom.lowercase()

        nativeAssets[lowered]?.let { return it }

        // "x/<name>" is the app-layer (Rujira) namespace for THOR-native tokens —
        // NOT a synth of a chain called "X"
        if (lowered.startsWith("x/")) {
            val symbol = lowered.removePrefix("x/").uppercase()
            return Asset("THOR", symbol, symbol.substringBefore('-'))
        }

        return if (lowered.none { it == '.' || it == '/' || it == '~' || it == '-' }) {
            // plain denom: a THOR-native token (like "tcy")
            Asset("THOR", lowered.uppercase(), lowered.uppercase())
        } else {
            // "chain-symbol" (secured) or "chain/symbol" (synth)
            Asset.fromString(lowered)
        }
    }

    // NOTE: a THOR-native Asset does not carry whether its bank denom uses the plain
    // ("tcy") or the "x/" ("x/ruji") notation — only denoms in nativeAssets round-trip
    // exactly. For sends, pass the bank denom string itself; never derive it via
    // denomFor for an unknown x/-token.
    override fun denomFor(asset: Asset): String {
        nativeAssets.entries.firstOrNull { it.value == asset }?.let { return it.key }

        return if (asset.chain == "THOR" && !asset.synth && !asset.trade && !asset.secured) {
            asset.symbol.lowercase()
        } else {
            asset.toString().lowercase()
        }
    }
}
