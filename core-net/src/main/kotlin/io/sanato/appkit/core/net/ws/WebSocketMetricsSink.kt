package io.sanato.appkit.core.net.ws

/**
 * 长连接的遥测落点。刻意【不】复用 `NetworkMetricsSink`:那个接口的形状是
 * "一次请求一次回调"(routeTemplate/method/httpStatus/totalMillis/failed),
 * 长连接没有 method、没有 httpStatus,totalMillis 无意义,而它真正需要表达的
 * 连接次数/重连次数/会话时长/消息计数一个都放不进去。给它加第二个方法会同时
 * 破坏 SAM 转换和 `core-net`/`net-telemetry-hilt` 两份 API golden。
 *
 * 单方法 + sealed 事件:保住 `fun interface`,未来加事件类型只是给一个全新的
 * sealed 层级加分支,不影响这里的 SAM 形状。
 */
fun interface WebSocketMetricsSink {
    fun onWebSocketEvent(event: WebSocketMetricEvent)
}

sealed interface WebSocketMetricEvent {
    /**
     * `host + encodedPath`,【永远不含 query】——[TokenPlacement.QueryParameter]
     * 下 token 就在 query 里,把完整 URL 送进遥测等于把凭证写进分析后台。同
     * `TelemetryEventListenerFactory` 里"路由模板化防基数爆炸"是同一套理由。
     */
    val endpoint: String

    data class Opened(
        override val endpoint: String,
        val handshakeMillis: Long,
        val attempt: Int,
    ) : WebSocketMetricEvent

    data class Reconnecting(
        override val endpoint: String,
        val attempt: Int,
        val delayMillis: Long,
        val reason: String,
    ) : WebSocketMetricEvent

    /**
     * 会话结束时【一次性】汇总消息计数。
     *
     * ⚠️ 刻意没有 per-message 事件:一个高频连接会产生每秒多次的遥测调用,
     * 在 Firebase Analytics 这类后端上会直接触发配额限流并淹没其他事件——
     * 这正是"一次请求一次回调"的形状在长连接上不成立的核心原因:不是它缺
     * 字段,是它的【频率模型】不对。
     */
    data class Closed(
        override val endpoint: String,
        val code: Int,
        val sessionMillis: Long,
        val messagesIn: Long,
        val messagesOut: Long,
        val bytesIn: Long,
        val bytesOut: Long,
        val clean: Boolean,
    ) : WebSocketMetricEvent

    data class Failed(
        override val endpoint: String,
        val reason: String,
        val sessionMillis: Long,
        val attempt: Int,
    ) : WebSocketMetricEvent
}
