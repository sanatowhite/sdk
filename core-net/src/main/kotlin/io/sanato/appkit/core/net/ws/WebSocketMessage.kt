package io.sanato.appkit.core.net.ws

import okio.ByteString

/**
 * 文本帧和二进制帧刻意不强行统一——OkHttp 的 `WebSocketListener.onMessage(String)` /
 * `onMessage(ByteString)` 本来就是两条回调,硬统一成 `ByteArray` 会让文本协议的
 * 消费方每条消息多一次编解码。
 *
 * 用 [ByteString] 而不是 `ByteArray`:它是不可变的。[WebSocketConnection.messages]
 * 是一个会扇出到多个收集者的 `SharedFlow`,共享一个可变 `ByteArray` 引用是真实的
 * 数据竞争。`okio` 已经通过 `okhttp` 的 `api()` 传递给消费方,不引入新坐标。
 */
sealed interface WebSocketMessage {
    data class Text(
        val value: String,
    ) : WebSocketMessage

    data class Binary(
        val bytes: ByteString,
    ) : WebSocketMessage
}
