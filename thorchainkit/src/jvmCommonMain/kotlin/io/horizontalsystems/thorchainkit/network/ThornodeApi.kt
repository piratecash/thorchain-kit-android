package io.horizontalsystems.thorchainkit.network

import com.google.gson.JsonObject
import com.google.gson.annotations.SerializedName
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface ThornodeApi {

    @GET("cosmos/bank/v1beta1/balances/{address}")
    suspend fun balances(
        @Path("address") address: String,
        @Query("pagination.limit") limit: Int = 1000
    ): BalancesResponse

    @GET("cosmos/auth/v1beta1/accounts/{address}")
    suspend fun account(@Path("address") address: String): AccountResponse

    // protocol path segment is chain-driven ("thorchain" / "mayachain"); Retrofit fills
    // every {protocol} occurrence from the single argument. The constants endpoint carries
    // the native tx fee under the same `NativeTransactionFee` key on both chains (in the
    // chain's base units), unlike /network whose fee field name is chain-specific
    @GET("{protocol}/constants")
    suspend fun constants(@Path(value = "protocol", encoded = true) protocol: String): ConstantsResponse

    // the height is keyed by the protocol module name ("thorchain" / "mayachain"), which
    // differs per chain, so the entry is parsed generically in ThornodeApiProvider
    @GET("{protocol}/lastblock/{protocol}")
    suspend fun lastBlock(@Path(value = "protocol", encoded = true) protocol: String): List<JsonObject>

    @GET("cosmos/base/tendermint/v1beta1/node_info")
    suspend fun nodeInfo(): NodeInfoResponse

    @POST("cosmos/tx/v1beta1/txs")
    suspend fun broadcast(@Body request: BroadcastRequest): BroadcastResponse

    @GET("cosmos/tx/v1beta1/txs/{hash}")
    suspend fun transaction(@Path("hash") hash: String): TxByHashResponse
}

// All response DTO fields are nullable on purpose: Gson populates them via reflection and
// bypasses Kotlin null-safety, so a malformed provider response would otherwise smuggle
// nulls (or primitive defaults like code == 0) into "non-null" types. Validation into
// non-null domain values happens in ThornodeApiProvider.

data class BalancesResponse(
    val balances: List<CoinResponse>?
)

data class CoinResponse(
    val denom: String?,
    val amount: String?
)

// account payload varies: BaseAccount fields are top-level, ModuleAccount nests
// them under base_account — parsed manually in ThornodeApiProvider
data class AccountResponse(
    val account: JsonObject?
)

data class ConstantsResponse(
    @SerializedName("int_64_values") val int64Values: Int64Values?
) {
    data class Int64Values(
        @SerializedName("NativeTransactionFee") val nativeTransactionFee: Long?
    )
}

data class NodeInfoResponse(
    @SerializedName("default_node_info") val defaultNodeInfo: DefaultNodeInfo?
) {
    data class DefaultNodeInfo(
        val network: String?
    )
}

data class BroadcastRequest(
    @SerializedName("tx_bytes") val txBytes: String,
    val mode: String = "BROADCAST_MODE_SYNC"
)

data class BroadcastResponse(
    @SerializedName("tx_response") val txResponse: TxResponse?
)

data class TxByHashResponse(
    @SerializedName("tx_response") val txResponse: TxResponse?
)

data class TxResponse(
    val height: String?,
    val txhash: String?,
    val codespace: String?,
    val code: Int?,
    @SerializedName("raw_log") val rawLog: String?
)
