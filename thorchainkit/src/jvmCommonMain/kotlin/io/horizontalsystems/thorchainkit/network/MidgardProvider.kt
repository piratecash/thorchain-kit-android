package io.horizontalsystems.thorchainkit.network

import kotlinx.coroutines.CancellationException
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.net.URL

// the api list is the injection point for tests; real instances come from `create`.
// it cannot be a second constructor: List<URL> and List<MidgardApi> erase to the
// same JVM signature
class MidgardProvider internal constructor(
    private val apis: List<MidgardApi>
) {

    suspend fun fetchActions(
        address: String,
        limit: Int = 50,
        nextPageToken: String? = null,
        txId: String? = null
    ): Pair<List<MidgardAction>, String?> {
        var lastError: Throwable? = null

        apis.forEach { api ->
            try {
                val response = api.actions(address, limit, nextPageToken, txId)
                val actions = response.actions
                    ?: throw InvalidProviderResponse("actions: missing 'actions' field")

                return Pair(actions, response.meta?.nextPageToken?.takeIf { it.isNotEmpty() })
            } catch (error: HttpException) {
                if (error.code() < 500) throw error
                lastError = error
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                lastError = error
            }
        }

        throw lastError ?: IllegalStateException("No midgard providers configured")
    }

    companion object {

        fun create(baseUrls: List<URL>) = MidgardProvider(
            baseUrls.map {
                Retrofit.Builder()
                    .baseUrl(it.toString())
                    .client(ApiClient.build())
                    .addConverterFactory(GsonConverterFactory.create())
                    .build()
                    .create(MidgardApi::class.java)
            }
        )
    }
}
