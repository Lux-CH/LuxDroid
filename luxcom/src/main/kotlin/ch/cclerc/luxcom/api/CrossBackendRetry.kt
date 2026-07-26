package ch.cclerc.luxcom.api

import ch.cclerc.luxcom.net.ApiClient
import ch.cclerc.luxcom.net.ApiError
import kotlin.coroutines.cancellation.CancellationException

internal const val UNKNOWN_TIMETABLE_LOCATION = "Could not find timetable location"
internal const val UNKNOWN_STOP_MARKER = "stop_found=false"
internal const val UNKNOWN_ENTITY_MARKER = "Could not find"

internal fun isUnknownEntityError(description: String): Boolean =
    description.contains(UNKNOWN_STOP_MARKER) || description.contains(UNKNOWN_ENTITY_MARKER)

internal suspend inline fun <reified T> fetchWithCrossBackendRetry(
    endpoint: String,
    apiVersion: String,
    queryItems: List<Pair<String, String>>
): T = try {
    ApiClient.fetch(endpoint = endpoint, apiVersion = apiVersion, queryItems = queryItems)
} catch (error: ApiError.RequestFailed) {
    if (!isUnknownEntityError(error.description)) throw error
    try {
        ApiClient.fetch(
            endpoint = endpoint,
            apiVersion = apiVersion,
            queryItems = queryItems,
            baseUrl = ApiClient.crossBackendRetryUrl()
        )
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (retryError: Throwable) {
        throw error
    }
}
