package io.sanato.apptemplate.ui.websocket

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import io.sanato.appkit.core.common.AppResult
import io.sanato.appkit.core.common.isDebuggableBuild
import io.sanato.appkit.core.net.HttpClientFactory
import io.sanato.appkit.core.net.ws.WebSocketConfig
import io.sanato.appkit.core.net.ws.WebSocketConnection
import io.sanato.appkit.core.net.ws.WebSocketFactory
import io.sanato.appkit.core.net.ws.WebSocketMessage
import io.sanato.appkit.core.net.ws.WebSocketMetricsSink
import io.sanato.appkit.core.net.ws.WebSocketState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Postman 运营的公开 echo 测试服务——发什么原样回什么,不需要任何账号/API key。
 * 这个模板本身没有真实后端,选它只是为了让这个 demo 能连上一个真实的公网
 * WebSocket 服务端,而不是自己 mock 一个。见 https://blog.postman.com/introducing-postbot/
 * 及 Postman 官方文档里对这个地址的说明。
 */
private const val ECHO_WEBSOCKET_URL = "wss://ws.postman-echo.com/raw"

enum class WebSocketLogDirection { SENT, RECEIVED, SYSTEM }

data class WebSocketLogEntry(
    val direction: WebSocketLogDirection,
    val text: String,
)

data class WebSocketDemoUiState(
    val connectionState: WebSocketState = WebSocketState.Idle,
    val log: List<WebSocketLogEntry> = emptyList(),
    val draft: String = "",
) {
    val isConnected: Boolean get() = connectionState is WebSocketState.Connected
    val canSend: Boolean get() = isConnected && draft.isNotBlank()
}

/**
 * `:core-net` WebSocket 长连接能力的可玩 demo——刻意不接 `:auth-net-hilt` 的
 * `AuthWebSocketTokenProvider`:公网 echo 服务本身不校验任何凭证,强行带上 token
 * 只会让这个 demo 看起来"需要登录"而实际没有意义。认证 WebSocket 这条路径已经
 * 有 `:auth-net-hilt` 自己的单元测试覆盖,不需要在这里重复验证。
 *
 * ⚠️ 用 [viewModelScope] 作为 [WebSocketConnection] 的生命周期(`WebSocketFactory.create`
 * 的 KDoc 明确建议"传 app 级 scope,不要传 viewModelScope,长连接不该跟着一个屏幕死")——
 * 这里是刻意的例外:这就是一个"进这个 demo 页才连,离开就断"的页面级体验,不是真实
 * 业务里需要跨屏幕存活的长连接。真实消费方接这个 API 时应该遵循 KDoc 的建议,不要
 * 照抄这个 demo 的生命周期选择。
 */
@HiltViewModel
class WebSocketDemoViewModel
    @Inject
    constructor(
        @ApplicationContext context: Context,
        metricsSink: WebSocketMetricsSink,
    ) : ViewModel() {
        private val connection: WebSocketConnection =
            WebSocketFactory.create(
                client =
                    WebSocketFactory.webSocketOkHttpClient(
                        HttpClientFactory.okHttpClient(enableLogging = context.isDebuggableBuild()),
                    ),
                config = WebSocketConfig(url = ECHO_WEBSOCKET_URL),
                scope = viewModelScope,
                metricsSink = metricsSink,
            )

        private val log = MutableStateFlow<List<WebSocketLogEntry>>(emptyList())
        private val draft = MutableStateFlow("")

        val uiState: StateFlow<WebSocketDemoUiState> =
            combine(connection.state, log, draft) { connectionState, log, draft ->
                WebSocketDemoUiState(connectionState = connectionState, log = log, draft = draft)
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WebSocketDemoUiState())

        init {
            viewModelScope.launch {
                connection.messages.collect { message ->
                    val text =
                        when (message) {
                            is WebSocketMessage.Text -> message.value
                            is WebSocketMessage.Binary -> "<binary ${message.bytes.size} bytes>"
                        }
                    appendLog(WebSocketLogDirection.RECEIVED, text)
                }
            }
            // 一进 demo 页就自动连——"点进来就能体验",不需要用户再多点一次连接按钮。
            connection.connect()
        }

        fun onDraftChange(value: String) {
            draft.value = value
        }

        fun send() {
            val text = draft.value
            if (text.isBlank()) return
            draft.value = ""
            viewModelScope.launch {
                when (val result = connection.send(WebSocketMessage.Text(text))) {
                    is AppResult.Success -> {
                        appendLog(WebSocketLogDirection.SENT, text)
                    }

                    is AppResult.Failure -> {
                        appendLog(WebSocketLogDirection.SYSTEM, "发送失败:${result.error.message}")
                    }
                }
            }
        }

        fun connect() = connection.connect()

        fun disconnect() = connection.close()

        private fun appendLog(
            direction: WebSocketLogDirection,
            text: String,
        ) {
            log.update { it + WebSocketLogEntry(direction, text) }
        }

        override fun onCleared() {
            // 显式发一个干净的 close 帧,而不是只靠 viewModelScope 取消——见类级 KDoc
            // 里"为什么这里可以用 viewModelScope"的说明。
            connection.close()
            super.onCleared()
        }
    }
