package io.sanato.appkit.net.telemetry.hilt

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.sanato.appkit.core.net.NetworkMetricsSink
import io.sanato.appkit.core.telemetry.NetworkRequestReport
import io.sanato.appkit.core.telemetry.Telemetry
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
}
