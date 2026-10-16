package io.horizontalsystems.thorchainkit.transaction

import io.horizontalsystems.thorchainkit.models.Address
import io.horizontalsystems.thorchainkit.models.Asset
import java.math.BigInteger

/**
 * The SIGN_MODE_LEGACY_AMINO_JSON sign doc as thornode builds it (cosmos-sdk x/tx aminojson with thornode's bech32 and
 * asset encoders): sorted keys, no whitespace, integers as strings, strings escaped like Go's json.Marshal.
 */
internal object AminoJson {
    private const val MSG_SEND = "thorchain/MsgSend"
    private const val MSG_DEPOSIT = "thorchain/MsgDeposit"

    fun signDoc(
        messages: List<String>,
        memo: String,
        accountNumber: Long,
        sequence: Long,
        chainId: String,
        gasLimit: Long,
        fee: List<String>,
    ): ByteArray = obj(
        "account_number" to string(accountNumber.toString()),
        "chain_id" to string(chainId),
        "fee" to obj("amount" to array(fee), "gas" to string(gasLimit.toString())),
        "memo" to string(memo),
        "msgs" to array(messages),
        "sequence" to string(sequence.toString()),
    ).encodeToByteArray()

    fun coin(amount: BigInteger, denom: String): String =
        obj("amount" to string(amount.toString()), "denom" to string(denom))

    fun msgSend(from: Address, to: Address, amount: BigInteger, denom: String): String = message(
        MSG_SEND,
        obj(
            "amount" to array(listOf(coin(amount, denom))),
            "from_address" to string(from.toString()),
            "to_address" to string(to.toString()),
        ),
    )

    // The deposit memo is a plain proto3 string, so the encoder omits it when empty; decimals 0 is omitted the same way.
    fun msgDeposit(asset: Asset, amount: BigInteger, memo: String, signer: Address): String = message(
        MSG_DEPOSIT,
        obj(
            listOfNotNull(
                "coins" to array(listOf(obj("amount" to string(amount.toString()), "asset" to string(asset.toString())))),
                memo.takeIf { it.isNotEmpty() }?.let { "memo" to string(it) },
                "signer" to string(signer.toString()),
            ),
        ),
    )

    private fun message(type: String, value: String): String = obj("type" to string(type), "value" to value)

    private fun obj(vararg fields: Pair<String, String>): String = obj(fields.toList())

    private fun obj(fields: List<Pair<String, String>>): String =
        fields.sortedBy { it.first }.joinToString(",", "{", "}") { (key, value) -> "${string(key)}:$value" }

    private fun array(values: List<String>): String = values.joinToString(",", "[", "]")

    private fun string(value: String): String = buildString {
        append('"')
        value.forEach { append(it.goEscaped()) }
        append('"')
    }

    private fun Char.goEscaped(): String = when (this) {
        '"' -> "\\\""
        '\\' -> "\\\\"
        '\n' -> "\\n"
        '\r' -> "\\r"
        '\t' -> "\\t"
        '\b' -> "\\b"
        '\u000c' -> "\\f"
        '<', '>', '&', '\u2028', '\u2029' -> unicodeEscape()
        else -> if (this < ' ') unicodeEscape() else toString()
    }

    private fun Char.unicodeEscape(): String = "\\u%04x".format(code)
}
