package io.sanato.appkit.core.net

/**
 * `:core-common` 的 `AppResult.Failure` 只携带 `Throwable`——这里把网络层能遇到的
 * 具体错误来源精确分类之后,再包进 `AppResult`(见 [safeApiCall])。
 */
sealed class AppError(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause) {
    class Http(
        val code: Int,
        val body: String?,
    ) : AppError("HTTP $code")

    class Timeout(
        cause: Throwable,
    ) : AppError("request timed out", cause)

    class NoConnectivity(
        cause: Throwable,
    ) : AppError("no network connectivity", cause)

    class Ssl(
        cause: Throwable,
    ) : AppError("TLS/SSL handshake failed", cause)

    class Serialization(
        cause: Throwable,
    ) : AppError("response body could not be parsed", cause)

    class Unknown(
        cause: Throwable,
    ) : AppError(cause.message ?: "unknown error", cause)
}
