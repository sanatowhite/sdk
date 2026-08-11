package io.sanato.appkit.core.net.ws

import io.sanato.appkit.core.net.AppError
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

/**
 * 继承 [IOException] 而不是 [AppError]:
 *  1. 不碰 `AppError` 那个已冻结的 sealed 层级——给 sealed class 加子类在 javap
 *     快照层面是纯增量(`apiCheck` 会放行),但对消费方任何不带 `else` 的穷尽
 *     `when` 是源码破坏,闸门看不见这次真实的破坏(同 `SafeApiCall.kt` 拒绝
 *     `inline` 的理由:不要把 ABI 锁死的东西悄悄改掉)。
 *  2. `IOException` 是 OkHttp 回调里 `onFailure(t: Throwable)` 的自然基类,
 *     [Transport] 分支能原样保留 cause 的类型信息。
 *
 * 想统一进 `AppError` 的消费方调 [toAppError]。
 */
sealed class WebSocketError(
    message: String,
    cause: Throwable? = null,
) : IOException(message, cause) {
    /** 升级握手被拒——这本质就是一次 HTTP 响应。401/403 是"token 无效"的权威信号。 */
    class HandshakeRejected(
        val code: Int,
        val body: String?,
    ) : WebSocketError("websocket handshake rejected with HTTP $code")

    /** 对端主动关闭且 code 非 1000。 */
    class ClosedByPeer(
        val code: Int,
        val reason: String,
    ) : WebSocketError("peer closed the socket: $code $reason")

    /** `pingInterval` 内没收到 pong——半开连接的唯一检测手段。 */
    class PingTimeout : WebSocketError("no pong received within the ping interval")

    /** 入站缓冲溢出(见 [WebSocketOverflowPolicy.FAIL_CONNECTION])。 */
    class BackpressureOverflow(
        val capacity: Int,
    ) : WebSocketError("inbound buffer overflow (capacity=$capacity)")

    /** token provider 无法提供凭证(比如强制刷新也失败,或已登出)。不可重试。 */
    class Unauthenticated(
        cause: Throwable? = null,
    ) : WebSocketError("no valid credential available for the handshake", cause)

    /** 连接/读写层失败:DNS、TLS、RST、超时等。 */
    class Transport(
        cause: Throwable,
    ) : WebSocketError(cause.message ?: "websocket transport failure", cause)
}

/**
 * 单向桥:想让 WebSocket 失败和 HTTP 失败落进同一套 UI 错误映射的消费方用它。
 * 刻意只提供 WebSocketError → AppError 一个方向——反方向没有意义。
 */
fun WebSocketError.toAppError(): AppError =
    when (this) {
        is WebSocketError.HandshakeRejected -> {
            AppError.Http(code, body)
        }

        is WebSocketError.Transport -> {
            when (val c = cause) {
                is SocketTimeoutException -> AppError.Timeout(c)
                is UnknownHostException -> AppError.NoConnectivity(c)
                is SSLException -> AppError.Ssl(c)
                else -> AppError.Unknown(this)
            }
        }

        else -> {
            AppError.Unknown(this)
        }
    }
