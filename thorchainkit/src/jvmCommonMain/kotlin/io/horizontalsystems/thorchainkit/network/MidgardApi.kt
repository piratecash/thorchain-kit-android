package io.horizontalsystems.thorchainkit.network

import com.google.gson.JsonObject
import com.google.gson.annotations.SerializedName
import retrofit2.http.GET
import retrofit2.http.Query

interface MidgardApi {

    @GET("v2/actions")
    suspend fun actions(
        @Query("address") address: String,
        @Query("limit") limit: Int,
        @Query("nextPageToken") nextPageToken: String?,
        @Query("txid") txId: String?
    ): ActionsResponse
}

// Nullable DTO fields on purpose: Gson bypasses Kotlin null-safety, so malformed
// responses would otherwise smuggle nulls into non-null types. Validation happens
// in MidgardProvider / TransactionSyncer.

data class ActionsResponse(
    val actions: List<MidgardAction>?,
    val meta: Meta?
) {
    data class Meta(
        val nextPageToken: String?
    )
}

data class MidgardAction(
    val type: String?,
    val status: String?,
    val date: Long?,
    val height: Long?,
    @SerializedName("in") val incoming: List<ActionTx>?,
    @SerializedName("out") val outgoing: List<ActionTx>?,
    val pools: List<String>?,
    val metadata: JsonObject?
)

data class ActionTx(
    val address: String?,
    @SerializedName("txID") val txId: String?,
    val coins: List<ActionCoin>?
)

data class ActionCoin(
    val asset: String?,
    val amount: String?
)
