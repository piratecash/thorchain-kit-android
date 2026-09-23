package io.horizontalsystems.thorchainkit.models

// Maya (mayanode) bank denom notation. Native assets are bank denoms: "cacao" (10 decimals,
// the settlement asset) and "maya" (the governance token). L1 assets follow the same
// thornode delimiter grammar as THORChain: "arb/eth" (synth), "arb-...".
// Maya has no Rujira "x/" app-layer namespace, so (unlike ThorchainAssetResolver) plain
// delimiter-free denoms are treated as MAYA-native tokens directly.
object MayaAssetResolver : AssetResolver {

    override val nativeDenom = "cacao"
    override val decimals = 10

    // MAYA-native denoms whose notation is irregular (not derivable from the asset)
    private val nativeAssets = mapOf(
        nativeDenom to Asset("MAYA", "CACAO", "CACAO"),
        "maya" to Asset("MAYA", "MAYA", "MAYA")
    )

    override fun assetFor(denom: String): Asset {
        val lowered = denom.lowercase()

        nativeAssets[lowered]?.let { return it }

        return if (lowered.none { it == '.' || it == '/' || it == '~' || it == '-' }) {
            // plain denom: a MAYA-native token
            Asset("MAYA", lowered.uppercase(), lowered.uppercase())
        } else {
            // "chain-symbol" (secured) or "chain/symbol" (synth)
            Asset.fromString(lowered)
        }
    }

    override fun denomFor(asset: Asset): String {
        nativeAssets.entries.firstOrNull { it.value == asset }?.let { return it.key }

        return if (asset.chain == "MAYA" && !asset.synth && !asset.trade && !asset.secured) {
            asset.symbol.lowercase()
        } else {
            asset.toString().lowercase()
        }
    }
}
