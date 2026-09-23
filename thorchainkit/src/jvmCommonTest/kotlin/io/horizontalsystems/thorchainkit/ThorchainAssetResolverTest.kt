package io.horizontalsystems.thorchainkit

import io.horizontalsystems.thorchainkit.models.Asset
import io.horizontalsystems.thorchainkit.models.ThorchainAssetResolver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class ThorchainAssetResolverTest {

    private val resolver = ThorchainAssetResolver

    @Test
    fun assetFor_thorNatives() {
        assertEquals(Asset.Rune, resolver.assetFor("rune"))
        assertEquals(Asset("THOR", "TCY", "TCY"), resolver.assetFor("tcy"))
        assertEquals(Asset("THOR", "RUJI", "RUJI"), resolver.assetFor("x/ruji"))
    }

    @Test
    fun assetFor_genericXDenoms_areThorNative_notSynths() {
        // any x/<name> denom is a THOR-native (Rujira) token, not a synth of chain "X"
        val asset = resolver.assetFor("x/nami")

        assertEquals(Asset("THOR", "NAMI", "NAMI"), asset)
        assertFalse(asset.synth)
        assertFalse(asset.trade)
        assertFalse(asset.secured)

        assertEquals(Asset("THOR", "BOW-XYK-1", "BOW"), resolver.assetFor("x/bow-xyk-1"))
    }

    @Test
    fun assetFor_secured() {
        assertEquals(Asset("BTC", "BTC", "BTC", secured = true), resolver.assetFor("btc-btc"))
        assertEquals(
            Asset("ETH", "USDC-0XA0B86991C6218B36C1D19D4A2E9EB0CE3606EB48", "USDC", secured = true),
            resolver.assetFor("eth-usdc-0xa0b86991c6218b36c1d19d4a2e9eb0ce3606eb48")
        )
    }

    @Test
    fun assetFor_synth() {
        assertEquals(Asset("BTC", "BTC", "BTC", synth = true), resolver.assetFor("btc/btc"))
    }

    @Test
    fun denomFor_inverse() {
        // every assetFor result maps back to the original denom
        listOf(
            "rune",
            "tcy",
            "x/ruji",
            "btc-btc",
            "btc/btc",
            "eth-usdc-0xa0b86991c6218b36c1d19d4a2e9eb0ce3606eb48"
        ).forEach { denom ->
            assertEquals(denom, resolver.denomFor(resolver.assetFor(denom)))
        }
    }

    @Test
    fun denomFor_futureThorNative() {
        assertEquals("newtoken", resolver.denomFor(Asset("THOR", "NEWTOKEN", "NEWTOKEN")))
        assertEquals(Asset("THOR", "NEWTOKEN", "NEWTOKEN"), resolver.assetFor("newtoken"))
    }
}
