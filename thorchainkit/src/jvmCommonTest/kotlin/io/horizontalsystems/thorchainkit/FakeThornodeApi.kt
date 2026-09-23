package io.horizontalsystems.thorchainkit

import com.google.gson.JsonObject
import io.horizontalsystems.thorchainkit.network.AccountResponse
import io.horizontalsystems.thorchainkit.network.BalancesResponse
import io.horizontalsystems.thorchainkit.network.BroadcastRequest
import io.horizontalsystems.thorchainkit.network.BroadcastResponse
import io.horizontalsystems.thorchainkit.network.ConstantsResponse
import io.horizontalsystems.thorchainkit.network.NodeInfoResponse
import io.horizontalsystems.thorchainkit.network.ThornodeApi
import io.horizontalsystems.thorchainkit.network.TxByHashResponse
import okhttp3.ResponseBody.Companion.toResponseBody
import retrofit2.HttpException
import retrofit2.Response

// test double: every endpoint fails loudly unless a test overrides it
open class FakeThornodeApi : ThornodeApi {

    var broadcastCalls = 0

    override suspend fun balances(address: String, limit: Int): BalancesResponse =
        throw NotImplementedError("balances")

    override suspend fun account(address: String): AccountResponse =
        throw NotImplementedError("account")

    override suspend fun constants(protocol: String): ConstantsResponse =
        throw NotImplementedError("constants")

    override suspend fun lastBlock(protocol: String): List<JsonObject> =
        throw NotImplementedError("lastBlock")

    override suspend fun nodeInfo(): NodeInfoResponse =
        throw NotImplementedError("nodeInfo")

    override suspend fun broadcast(request: BroadcastRequest): BroadcastResponse =
        throw NotImplementedError("broadcast")

    override suspend fun transaction(hash: String): TxByHashResponse =
        throw NotImplementedError("transaction")

    companion object {
        fun httpException(code: Int): HttpException =
            HttpException(Response.error<Any>(code, "".toResponseBody(null)))
    }
}
