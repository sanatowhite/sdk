package io.sanato.apptemplate.core.common

/**
 * 通用结果包装,不与任何具体错误来源(网络/磁盘/序列化)绑定——
 * `:core-net` 的 `safeApiCall` 会把它具体化为携带 `AppError` 的版本。
 */
sealed interface AppResult<out T> {
    data class Success<out T>(
        val data: T,
    ) : AppResult<T>

    data class Failure(
        val error: Throwable,
    ) : AppResult<Nothing>
}

inline fun <T, R> AppResult<T>.map(transform: (T) -> R): AppResult<R> =
    when (this) {
        is AppResult.Success -> AppResult.Success(transform(data))
        is AppResult.Failure -> this
    }

inline fun <T> AppResult<T>.onSuccess(action: (T) -> Unit): AppResult<T> {
    if (this is AppResult.Success) action(data)
    return this
}

inline fun <T> AppResult<T>.onFailure(action: (Throwable) -> Unit): AppResult<T> {
    if (this is AppResult.Failure) action(error)
    return this
}
