package io.sanato.appkit.core.net.ws

import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * 重连退避策略。指数增长 + 抖动 + 上限截断,外加一条防真实故障的规则(见
 * [resetAfterConnectedFor])。
 */
data class WebSocketRetryPolicy(
    val maxAttempts: Int = Int.MAX_VALUE,
    val initialDelay: Duration = 1.seconds,
    val maxDelay: Duration = 60.seconds,
    val multiplier: Double = 2.0,
    /** 抖动比例:实际延迟 = base × (1 ± jitterRatio × random)。防雷鸣羊群。 */
    val jitterRatio: Double = 0.2,
    /**
     * 连接【稳定存活】多久之后才把 attempt 计数器清零。
     *
     * ⚠️ 这个参数是防一个具体的真实故障:如果在每次"连上"就清零,那么一个
     * "接受连接后立刻断开"的服务端会让客户端以 [initialDelay] 的频率永久热
     * 循环,退避完全失效。必须以"稳定存活时长"而不是"连上过"作为清零条件。
     */
    val resetAfterConnectedFor: Duration = 30.seconds,
)

/**
 * 入站缓冲满了怎么办。OkHttp 的 `WebSocketListener` 回调跑在 reader 线程上,
 * 在那里阻塞会连 pong / close 帧一起卡住,所以三种策略都不能无脑阻塞 reader
 * 线程(见 [SUSPEND_READER] 的说明)。
 */
enum class WebSocketOverflowPolicy {
    /**
     * 默认。溢出 ⇒ 以 [WebSocketError.BackpressureOverflow] 主动关闭连接并进入
     * 重连流程。
     *
     * 选它作默认的理由:消息协议里"静默丢一条"是最坏的失败模式——它不产生任何
     * 信号,bug 会在业务层以完全无关的形态出现。响亮地失败 + 重连是唯一诚实的
     * 默认。
     */
    FAIL_CONNECTION,

    /** 丢最旧的。只适合"只关心最新值"的流(行情、位置、心跳状态)。 */
    DROP_OLDEST,

    /**
     * Channel 满时【阻塞 reader 线程】直到有空位——真正的 TCP 级背压。只在
     * 服务端行为可控、且确实需要"一条都不能丢 + 让服务端慢下来"时用。代价:
     * pong 会被延迟,极端情况下会被服务端判定为超时断开。
     */
    SUSPEND_READER,
}

/** token 放在握手请求的哪里。Android 能设 header,所以 [Header] 是默认。 */
sealed interface TokenPlacement {
    data class Header(
        val name: String = "Authorization",
        val prefix: String = "Bearer ",
    ) : TokenPlacement

    /**
     * ⚠️ query 里的 token 会落进服务端 access log、代理日志和崩溃报告的 URL
     * 字段。只在服务端确实不支持 header 时用。遥测的 endpoint 标签会剥掉整个
     * query(见 [WebSocketMetricEvent]),但那只保护本地这一侧。
     */
    data class QueryParameter(
        val name: String = "access_token",
    ) : TokenPlacement

    /** 走 `Sec-WebSocket-Protocol: <prefix><token>`。 */
    data class Subprotocol(
        val prefix: String = "bearer.",
    ) : TokenPlacement
}

/**
 * 应用层心跳。RFC ping/pong(由 [WebSocketFactory.webSocketOkHttpClient] 的
 * `pingInterval` 驱动)对大多数服务端够用;少数服务端忽略 RFC ping、只认业务
 * 心跳消息,才需要这个。
 */
data class AppHeartbeat(
    val interval: Duration,
    val message: () -> WebSocketMessage,
)

data class WebSocketConfig(
    val url: String,
    val retry: WebSocketRetryPolicy = WebSocketRetryPolicy(),
    val inboundBufferCapacity: Int = 64,
    val overflowPolicy: WebSocketOverflowPolicy = WebSocketOverflowPolicy.FAIL_CONNECTION,
    val tokenPlacement: TokenPlacement = TokenPlacement.Header(),
    /** 附加到握手请求上的静态 header(协议版本、设备标识等)。 */
    val headers: Map<String, String> = emptyMap(),
    /** `close()` 之后等待对端回 close 帧的时长,超时直接 cancel 底层 socket。 */
    val closeTimeout: Duration = 5.seconds,
    val appHeartbeat: AppHeartbeat? = null,
)
