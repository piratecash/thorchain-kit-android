package io.horizontalsystems.thorchainkit.network

import io.horizontalsystems.thorchainkit.models.AssetResolver
import io.horizontalsystems.thorchainkit.models.MayaAssetResolver
import io.horizontalsystems.thorchainkit.models.ThorchainAssetResolver
import java.net.URL

enum class Network(
    val id: Int,
    val coinType: Int,
    val addressPrefix: String,
    // pinned chain-id: used for signing instead of the node-reported value, so a
    // malicious or misconfigured endpoint can never make the kit sign a transaction
    // that is valid on a different network
    val chainId: String,
    val thornodeUrls: List<URL>,
    val midgardUrls: List<URL>,
    // API path segment identifying the protocol module on the node, e.g. "thorchain" in
    // /thorchain/... (Maya uses "mayachain")
    val protocolPath: String,
    // native settlement asset denom, e.g. "rune"
    val nativeDenom: String,
    // decimal places of the native denom
    val decimals: Int,
    // chain tag used in fully-qualified asset identifiers, e.g. "THOR" in THOR.RUNE
    val assetChainTag: String,
    // per-chain denom <-> Asset translation strategy
    val assetResolver: AssetResolver
) {
    Mainnet(
        id = 1,
        coinType = 931,
        addressPrefix = "thor",
        chainId = "thorchain-1",
        thornodeUrls = listOf(
            URL("https://gateway.liquify.com/chain/thorchain_api/"),
            URL("https://thornode.thorchain.liquify.com/")
        ),
        midgardUrls = listOf(
            URL("https://gateway.liquify.com/chain/thorchain_midgard/")
        ),
        protocolPath = "thorchain",
        nativeDenom = "rune",
        decimals = 8,
        assetChainTag = "THOR",
        assetResolver = ThorchainAssetResolver
    ),
    // Public stagenet endpoints are in transition (Nine Realms endpoints are gone,
    // new URLs not yet published) — override base URLs via ThorchainKit until then
    Stagenet(
        id = 2,
        coinType = 931,
        addressPrefix = "sthor",
        chainId = "thorchain-stagenet-2",
        thornodeUrls = listOf(
            URL("https://stagenet-thornode.ninerealms.com/")
        ),
        midgardUrls = listOf(
            URL("https://stagenet-midgard.ninerealms.com/")
        ),
        protocolPath = "thorchain",
        nativeDenom = "rune",
        decimals = 8,
        assetChainTag = "THOR",
        assetResolver = ThorchainAssetResolver
    ),
    // Maya Protocol (mayanode, a thornode fork). Reuses SLIP-44 coin type 931; the "maya"
    // address prefix keeps it distinct from THORChain. Settlement asset is CACAO (10 decimals).
    MayaMainnet(
        id = 10,
        coinType = 931,
        addressPrefix = "maya",
        chainId = "mayachain-mainnet-v1",
        thornodeUrls = listOf(
            URL("https://mayanode.mayachain.info/")
        ),
        midgardUrls = listOf(
            URL("https://midgard.mayachain.info/")
        ),
        protocolPath = "mayachain",
        nativeDenom = "cacao",
        decimals = 10,
        assetChainTag = "MAYA",
        assetResolver = MayaAssetResolver
    )
}
