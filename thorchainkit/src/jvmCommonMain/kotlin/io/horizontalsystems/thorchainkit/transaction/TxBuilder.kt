package io.horizontalsystems.thorchainkit.transaction

import com.google.protobuf.Any
import com.google.protobuf.ByteString
import common.Common
import cosmos.base.v1beta1.CoinOuterClass
import cosmos.crypto.secp256k1.Keys
import cosmos.tx.signing.v1beta1.Signing.SignMode
import cosmos.tx.v1beta1.TxOuterClass
import io.horizontalsystems.hdwalletkit.Utils
import io.horizontalsystems.thorchainkit.models.Address
import io.horizontalsystems.thorchainkit.models.Asset
import types.MsgDepositOuterClass.MsgDeposit
import types.MsgSendOuterClass.MsgSend
import java.math.BigInteger
import java.security.MessageDigest

object TxBuilder {

    // THORChain registers its own message types (proto package `types`),
    // not the standard cosmos.bank.v1beta1 ones
    const val TYPE_URL_MSG_SEND = "/types.MsgSend"
    const val TYPE_URL_MSG_DEPOSIT = "/types.MsgDeposit"
    const val TYPE_URL_PUB_KEY = "/cosmos.crypto.secp256k1.PubKey"

    // gas limits as used by xchainjs; THORChain has no gas market, the limit just needs to be sufficient
    const val DEFAULT_GAS_LIMIT = 6_000_000L
    const val DEPOSIT_GAS_LIMIT = 600_000_000L

    // THORChain/Cosmos tx hash: uppercase hex SHA-256 of the raw signed tx bytes.
    // Computed locally so the broadcast outcome can be resolved (via tx lookup)
    // even when the broadcast request itself fails ambiguously.
    fun txHash(txRaw: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(txRaw)
        return digest.joinToString("") { "%02X".format(it) }
    }

    fun msgSend(from: Address, to: Address, amount: BigInteger, denom: String): Any {
        val message = MsgSend.newBuilder()
            .setFromAddress(ByteString.copyFrom(from.payload))
            .setToAddress(ByteString.copyFrom(to.payload))
            .addAmount(
                CoinOuterClass.Coin.newBuilder()
                    .setDenom(denom)
                    .setAmount(amount.toString())
            )
            .build()

        return Any.newBuilder()
            .setTypeUrl(TYPE_URL_MSG_SEND)
            .setValue(message.toByteString())
            .build()
    }

    fun msgDeposit(asset: Asset, amount: BigInteger, memo: String, signer: Address, decimals: Long = 0): Any {
        val message = MsgDeposit.newBuilder()
            .addCoins(
                Common.Coin.newBuilder()
                    .setAsset(
                        Common.Asset.newBuilder()
                            .setChain(asset.chain)
                            .setSymbol(asset.symbol)
                            .setTicker(asset.ticker)
                            .setSynth(asset.synth)
                            .setTrade(asset.trade)
                            .setSecured(asset.secured)
                    )
                    .setAmount(amount.toString())
                    .setDecimals(decimals)
            )
            .setMemo(memo)
            .setSigner(ByteString.copyFrom(signer.payload))
            .build()

        return Any.newBuilder()
            .setTypeUrl(TYPE_URL_MSG_DEPOSIT)
            .setValue(message.toByteString())
            .build()
    }

    fun buildSigned(
        messages: List<Any>,
        memo: String,
        accountNumber: Long,
        sequence: Long,
        chainId: String,
        gasLimit: Long,
        feeAmount: BigInteger? = null,
        // native settlement denom of the target chain (e.g. "rune" / "cacao"); supplied
        // by the caller since TxBuilder is chain-agnostic. Only used when feeAmount != null.
        feeDenom: String,
        signer: Signer
    ): ByteArray {
        val bodyBytes = TxOuterClass.TxBody.newBuilder()
            .addAllMessages(messages)
            .setMemo(memo)
            .build()
            .toByteString()

        val authInfoBytes = TxOuterClass.AuthInfo.newBuilder()
            .addSignerInfos(
                TxOuterClass.SignerInfo.newBuilder()
                    .setPublicKey(
                        Any.newBuilder()
                            .setTypeUrl(TYPE_URL_PUB_KEY)
                            .setValue(
                                Keys.PubKey.newBuilder()
                                    .setKey(ByteString.copyFrom(signer.publicKey))
                                    .build()
                                    .toByteString()
                            )
                    )
                    .setModeInfo(
                        TxOuterClass.ModeInfo.newBuilder()
                            .setSingle(
                                TxOuterClass.ModeInfo.Single.newBuilder()
                                    .setMode(SignMode.SIGN_MODE_DIRECT)
                            )
                    )
                    .setSequence(sequence)
            )
            .setFee(
                TxOuterClass.Fee.newBuilder()
                    .setGasLimit(gasLimit)
                    .apply {
                        feeAmount?.let {
                            addAmount(
                                CoinOuterClass.Coin.newBuilder()
                                    .setDenom(feeDenom)
                                    .setAmount(it.toString())
                            )
                        }
                    }
            )
            .build()
            .toByteString()

        val signDoc = TxOuterClass.SignDoc.newBuilder()
            .setBodyBytes(bodyBytes)
            .setAuthInfoBytes(authInfoBytes)
            .setChainId(chainId)
            .setAccountNumber(accountNumber)
            .build()

        val signature = signer.sign(Utils.sha256(signDoc.toByteArray()))

        return TxOuterClass.TxRaw.newBuilder()
            .setBodyBytes(bodyBytes)
            .setAuthInfoBytes(authInfoBytes)
            .addSignatures(ByteString.copyFrom(signature))
            .build()
            .toByteArray()
    }
}
