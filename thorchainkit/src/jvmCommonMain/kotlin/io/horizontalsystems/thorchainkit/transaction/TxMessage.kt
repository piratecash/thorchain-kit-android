package io.horizontalsystems.thorchainkit.transaction

import com.google.protobuf.Any as ProtoAny
import io.horizontalsystems.thorchainkit.models.Address
import io.horizontalsystems.thorchainkit.models.Asset
import java.math.BigInteger

/** A message in both forms the chain checks: the protobuf of the tx body and the amino JSON of the sign doc. */
internal sealed class TxMessage {
    abstract fun proto(): ProtoAny

    abstract fun aminoJson(): String

    class Send(
        private val from: Address,
        private val to: Address,
        private val amount: BigInteger,
        private val denom: String,
    ) : TxMessage() {
        override fun proto(): ProtoAny = TxBuilder.msgSend(from, to, amount, denom)

        override fun aminoJson(): String = AminoJson.msgSend(from, to, amount, denom)
    }

    class Deposit(
        private val asset: Asset,
        private val amount: BigInteger,
        private val memo: String,
        private val signer: Address,
    ) : TxMessage() {
        override fun proto(): ProtoAny = TxBuilder.msgDeposit(asset, amount, memo, signer)

        override fun aminoJson(): String = AminoJson.msgDeposit(asset, amount, memo, signer)
    }
}
