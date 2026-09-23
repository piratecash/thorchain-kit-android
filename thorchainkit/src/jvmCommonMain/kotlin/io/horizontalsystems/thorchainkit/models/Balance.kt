package io.horizontalsystems.thorchainkit.models

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.math.BigInteger

@Entity
data class Balance(
    @PrimaryKey
    val denom: String,
    val amount: BigInteger
)
