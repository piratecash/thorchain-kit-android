package io.horizontalsystems.thorchainkit

import io.horizontalsystems.thorchainkit.models.Asset
import io.horizontalsystems.thorchainkit.models.MayaAssetResolver
import org.junit.Assert.assertEquals
import org.junit.Test

class MayaAssetResolverTest {

    private val resolver = MayaAssetResolver

    @Test
    fun config() {
        assertEquals("cacao", resolver.nativeDenom)
        assertEquals(10, resolver.decimals)
    }

    @Test
    fun assetFor_mayaNatives() {
        assertEquals(Asset("MAYA", "CACAO", "CACAO"), resolver.assetFor("cacao"))
        assertEquals(Asset("MAYA", "MAYA", "MAYA"), resolver.assetFor("maya"))
    }

    @Test
    fun assetFor_plainDenom_isMayaNative() {
        // no Rujira "x/" namespace on Maya: a delimiter-free denom is a MAYA-native token
        assertEquals(Asset("MAYA", "AZTEC", "AZTEC"), resolver.assetFor("aztec"))
    }

    @Test
    fun assetFor_synth() {
        // Maya L1 assets appear as synth bank denoms: "arb/eth", "arb/usdc-0x..."
        assertEquals(Asset("ARB", "ETH", "ETH", synth = true), resolver.assetFor("arb/eth"))
        assertEquals(
            Asset("ARB", "USDC-0XAF88D065E77C8CC2239327C5EDB3A432268E5831", "USDC", synth = true),
            resolver.assetFor("arb/usdc-0xaf88d065e77c8cc2239327c5edb3a432268e5831")
        )
    }

    @Test
    fun denomFor_inverse() {
        listOf(
            "cacao",
            "maya",
            "aztec",
            "arb/eth",
            "arb/usdc-0xaf88d065e77c8cc2239327c5edb3a432268e5831"
        ).forEach { denom ->
            assertEquals(denom, resolver.denomFor(resolver.assetFor(denom)))
        }
    }
}
