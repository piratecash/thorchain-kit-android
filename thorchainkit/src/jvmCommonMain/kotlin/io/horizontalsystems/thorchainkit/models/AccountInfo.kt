package io.horizontalsystems.thorchainkit.models

import java.math.BigInteger

data class AccountInfo(
    val accountNumber: Long,
    val sequence: Long
)

data class DenomBalance(
    val denom: String,
    val amount: BigInteger
)
