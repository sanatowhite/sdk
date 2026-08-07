package io.sanato.appkit.smoke

import io.sanato.appkit.core.net.HttpClientFactory
import io.sanato.appkit.core.net.NetworkMetricsSink
import io.sanato.appkit.core.net.safeApiCall
import javax.inject.Inject

/**
 * 逐个引用 `:core-net` 的公开入口——这些正是 `api()` 判定最容易出错的地方
 * (OkHttp/Retrofit/kotlinx.serialization 类型直接出现在参数/返回值里)。
 * 只需要能编译通过:任何一个类型被错误声明成 `implementation` 而非 `api`,
 * 这个文件就会因为"unresolved reference"编译失败。
 */
class NetSmoke
    @Inject
    constructor(
        // NetworkMetricsSink 由 :net-telemetry-hilt 提供绑定——这是唯一跨
        // core-net/core-telemetry 的桥,只有同时引了两边才需要它。
        private val metricsSink: NetworkMetricsSink,
    ) {
        fun buildClient() = HttpClientFactory.okHttpClient(enableLogging = false, metricsSink = metricsSink)

        fun buildRetrofit() = HttpClientFactory.retrofit(baseUrl = "https://example.invalid/", client = buildClient())

        suspend fun callSafely() = safeApiCall { "ok" }
    }
