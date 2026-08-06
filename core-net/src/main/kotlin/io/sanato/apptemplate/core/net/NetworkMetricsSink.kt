package io.sanato.apptemplate.core.net

/**
 * 网络耗时上报的落点接口——归 `:core-net` 所有,不是 `:core-telemetry`,这样
 * `core-net` 完全不需要依赖 `core-telemetry`。`:app` 提供桥接实现,连接两边,
 * 两个 core 模块之间保持零依赖边。
 */
fun interface NetworkMetricsSink {
    fun onRequestCompleted(
        routeTemplate: String,
        method: String,
        httpStatus: Int?,
        totalMillis: Long,
        failed: Boolean,
    )
}
