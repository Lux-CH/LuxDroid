package ch.cclerc.luxcom.net

sealed class ApiError(message: String? = null, cause: Throwable? = null) : Exception(message, cause) {
    class InvalidUrl : ApiError()

    class RequestFailed(val statusCode: Int, val description: String) : ApiError(description)

    class DecodingFailed(cause: Throwable) : ApiError(cause = cause)
}
