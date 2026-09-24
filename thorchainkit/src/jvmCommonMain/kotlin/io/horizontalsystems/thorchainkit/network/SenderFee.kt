package io.horizontalsystems.thorchainkit.network

import io.horizontalsystems.thorchainkit.models.AssetResolver
import java.math.BigInteger
import java.util.Base64

// The chain charges the native fee inside the message handler, so the tx has no fee field:
// the fee is what the sender's native balance lost beyond the amounts its messages moved.
// A response that does not fully add up throws, so the lookup stays open instead of storing a guess.
internal object SenderFee {

    private const val TRANSFER = "transfer"
    private const val SENDER = "sender"
    private const val AMOUNT = "amount"
    private const val TX_BY_HASH = "tx by hash"
    private const val MSG_SEND = ".MsgSend"
    private const val MSG_DEPOSIT = ".MsgDeposit"
    private val coinPattern = Regex("^(\\d+)([a-zA-Z][a-zA-Z0-9/:._~-]*)$")

    private class Transfer(val sender: String, val amount: String)

    // null when the sender was not charged or a message type is unknown
    internal fun of(tx: TxDetails, sender: String, assetResolver: AssetResolver): BigInteger? {
        val code = tx.code ?: throw malformed("missing code")
        val events = tx.events ?: throw malformed("missing events")
        val spent = nativeSpent(events, sender, assetResolver.nativeDenom)
        // a failed tx moved nothing but the fee
        val moved = if (code == 0) messagesNativeAmount(tx, assetResolver) ?: return null else BigInteger.ZERO
        return (spent - moved).takeIf { it.signum() > 0 }
    }

    private fun nativeSpent(events: List<TxEvent>, sender: String, nativeDenom: String): BigInteger =
        events.filter { it.type == TRANSFER }
            .map { transfer(it) }
            .filter { it.sender == sender }
            .sumOf { nativeAmount(it.amount, nativeDenom) }

    // Mayanode base64-encodes event attributes, THORNode does not
    private fun transfer(event: TxEvent): Transfer {
        val attributes = event.attributes ?: throw malformed("transfer without attributes")
        val plain = attributes.any { it.key == SENDER }
        val decoded = attributes.associate { attribute ->
            if (plain) attribute.key to attribute.value else attribute.key?.let(::decode) to attribute.value?.let(::decode)
        }
        return Transfer(
            sender = decoded[SENDER] ?: throw malformed("transfer without sender"),
            amount = decoded[AMOUNT] ?: throw malformed("transfer without amount")
        )
    }

    private fun messagesNativeAmount(tx: TxDetails, assetResolver: AssetResolver): BigInteger? {
        val messages = tx.tx?.body?.messages ?: throw malformed("missing messages")
        return messages.sumOf { messageNativeAmount(it, assetResolver) ?: return null }
    }

    // A known message must list every coin it moves; an unknown one leaves the fee unknown.
    private fun messageNativeAmount(message: TxDetails.Message, assetResolver: AssetResolver): BigInteger? {
        val type = message.type ?: throw malformed("message without @type")
        val nativeDenom = assetResolver.nativeDenom
        val (coins, native) = when {
            type.endsWith(MSG_SEND) -> message.amount?.map { it.denom to it.amount } to nativeDenom
            type.endsWith(MSG_DEPOSIT) ->
                message.coins?.map { it.asset to it.amount } to assetResolver.assetFor(nativeDenom).toString()
            else -> return null
        }
        return (coins ?: throw malformed("$type without coins")).sumOf { (id, amount) ->
            val coinId = id ?: throw malformed("$type coin without denom or asset")
            if (!coinId.equals(native, ignoreCase = true)) return@sumOf BigInteger.ZERO
            ThornodeApiProvider.parseAmount(amount, TX_BY_HASH)
        }
    }

    // an event amount lists coins as "2000000rune,5maya"
    private fun nativeAmount(value: String, nativeDenom: String): BigInteger =
        value.split(',')
            .map { coinPattern.find(it.trim()) ?: throw malformed("invalid coins: $value") }
            .filter { it.groupValues[2] == nativeDenom }
            .sumOf { BigInteger(it.groupValues[1]) }

    private fun decode(value: String): String =
        try {
            String(Base64.getDecoder().decode(value))
        } catch (error: IllegalArgumentException) {
            throw malformed("invalid base64 attribute")
        }

    private fun malformed(reason: String): InvalidProviderResponse = InvalidProviderResponse("$TX_BY_HASH: $reason")
}
