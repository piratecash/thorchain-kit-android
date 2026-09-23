package io.horizontalsystems.thorchainkit.models

// Per-chain strategy for translating between a chain's bank-denom notation and Asset.
// thornode-family chains share the delimiter grammar (Asset) but differ in their native
// denom, decimals, and the irregular native-token naming rules — those live here.
interface AssetResolver {
    val nativeDenom: String
    val decimals: Int

    fun assetFor(denom: String): Asset
    fun denomFor(asset: Asset): String
}
