package io.horizontalsystems.thorchainkit.models

import androidx.room.Entity
import androidx.room.PrimaryKey

// A wallet-sent transaction whose fee is asked from the node once; resolved rows are never asked again.
@Entity
internal data class FeeLookup(
    @PrimaryKey
    val hash: String,
    val resolved: Boolean = false
)
