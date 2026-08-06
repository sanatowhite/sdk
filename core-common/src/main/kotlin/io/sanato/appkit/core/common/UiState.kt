package io.sanato.appkit.core.common

/**
 * 组合态用 data class + 可空字段,不用 sealed class——
 * "加载中但已有旧数据"(下拉刷新)这类组合在 sealed 建模下需要额外的中间状态类,
 * 且随着字段增多状态数会指数爆炸。
 */
data class UiState<T>(
    val data: T? = null,
    val isLoading: Boolean = false,
    val error: Throwable? = null,
) {
    val isEmpty: Boolean
        get() = !isLoading && error == null && data == null

    companion object {
        fun <T> loading(previous: T? = null): UiState<T> = UiState(data = previous, isLoading = true)

        fun <T> success(data: T): UiState<T> = UiState(data = data)

        fun <T> failure(
            error: Throwable,
            previous: T? = null,
        ): UiState<T> = UiState(data = previous, error = error)
    }
}
