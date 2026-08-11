package io.sanato.appkit.core.net.ws

/**
 * `sealed interface` 而不是 `enum`——[Reconnecting]/[Failed] 必须携带上下文
 * (第几次尝试、等多久、为什么),enum 做不到。
 *
 * [Connected] 刻意不携带 `since` 时间戳:那会让每个测试断言都变得不确定,而
 * "连了多久"是遥测该算的(见 [WebSocketMetricEvent.Closed]),不是状态该带的。
 */
sealed interface WebSocketState {
    /** 还没调过 `connect()`。 */
    data object Idle : WebSocketState

    data object Connecting : WebSocketState

    data object Connected : WebSocketState

    data class Reconnecting(
        val attempt: Int,
        val delayMillis: Long,
        val cause: WebSocketError,
    ) : WebSocketState

    /** 正常关闭(本端 `close()` 或对端 1000)。不会自动重连。 */
    data class Closed(
        val code: Int,
        val reason: String,
    ) : WebSocketState

    /** 重连策略已放弃(次数耗尽或遇到不可重试的错误)。需要外部显式 `connect()` 才会再试。 */
    data class Failed(
        val error: WebSocketError,
    ) : WebSocketState
}
