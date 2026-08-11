package io.sanato.appkit.net.telemetry.hilt

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.sanato.appkit.core.net.NetworkMetricsSink
import io.sanato.appkit.core.net.ws.WebSocketMetricEvent
import io.sanato.appkit.core.net.ws.WebSocketMetricsSink
import io.sanato.appkit.core.telemetry.NetworkRequestReport
import io.sanato.appkit.core.telemetry.Telemetry
import io.sanato.appkit.core.telemetry.eventIfEnabled
import javax.inject.Singleton

/**
 * `NetworkMetricsSink` 接口归 `:core-net` 所有,这里只是桥接实现,把网络层
 * 的耗时数据转发进 `Telemetry`。
 */
@Module
@InstallIn(SingletonComponent::class)
object NetworkTelemetryBridgeModule {
    @Provides
    @Singleton
    fun provideNetworkMetricsSink(telemetry: Telemetry): NetworkMetricsSink =
        NetworkMetricsSink { routeTemplate, method, httpStatus, totalMillis, failed ->
            telemetry.networkRequest(
                NetworkRequestReport(
                    routeTemplate = routeTemplate,
                    method = method,
                    httpStatus = httpStatus,
                    totalMillis = totalMillis,
                    failed = failed,
                ),
            )
        }

    /**
     * `WebSocketMetricsSink` 接口归 `:core-net` 所有(它的 `ws` 子包),同样只是
     * 桥接实现。走 `Telemetry.eventIfEnabled` 逃生口而不是给 `Telemetry` 加强
     * 类型方法——加方法要同时改 `:core-telemetry` 的 golden、`SamplingTelemetry`、
     * `FirebaseTelemetry`、`LogKitTelemetry` 四处;`event()` 存在就是为了这个。
     */
    @Provides
    @Singleton
    fun provideWebSocketMetricsSink(telemetry: Telemetry): WebSocketMetricsSink =
        WebSocketMetricsSink { event ->
            val name =
                when (event) {
                    is WebSocketMetricEvent.Opened -> "ws_opened"
                    is WebSocketMetricEvent.Reconnecting -> "ws_reconnecting"
                    is WebSocketMetricEvent.Closed -> "ws_closed"
                    is WebSocketMetricEvent.Failed -> "ws_failed"
                }
            telemetry.eventIfEnabled(name) { event.toParams() }
        }

    private fun WebSocketMetricEvent.toParams(): Map<String, Any?> =
        when (this) {
            is WebSocketMetricEvent.Opened -> {
                mapOf("endpoint" to endpoint, "handshake_millis" to handshakeMillis, "attempt" to attempt)
            }

            is WebSocketMetricEvent.Reconnecting -> {
                mapOf("endpoint" to endpoint, "attempt" to attempt, "delay_millis" to delayMillis, "reason" to reason)
            }

            is WebSocketMetricEvent.Closed -> {
                mapOf(
                    "endpoint" to endpoint,
                    "code" to code,
                    "session_millis" to sessionMillis,
                    "messages_in" to messagesIn,
                    "messages_out" to messagesOut,
                    "bytes_in" to bytesIn,
                    "bytes_out" to bytesOut,
                    "clean" to clean,
                )
            }

            is WebSocketMetricEvent.Failed -> {
                mapOf(
                    "endpoint" to endpoint,
                    "reason" to reason,
                    "session_millis" to sessionMillis,
                    "attempt" to attempt,
                )
            }
        }
}
