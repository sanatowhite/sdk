package io.sanato.appkit.core.net

import kotlinx.serialization.json.Json
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlin.time.Duration.Companion.seconds

/**
 * 组装的入口点,不是具体某个后端的 API 客户端——具体的 Retrofit service 接口
 * 由消费方(`:app` 或独立复用这个模块的项目)自己定义。
 */
object HttpClientFactory {
    val defaultJson: Json =
        Json {
            ignoreUnknownKeys = true
            explicitNulls = false
        }

    /**
     * 超时用 `kotlin.time.Duration`(OkHttp 5 起 Builder 直接接受),不用
     * `TimeUnit` 分开传数字——同一个类型既表意又不会传错单位。
     */
    fun okHttpClient(
        enableLogging: Boolean = false,
        additionalInterceptors: List<Interceptor> = emptyList(),
        metricsSink: NetworkMetricsSink? = null,
    ): OkHttpClient =
        OkHttpClient
            .Builder()
            .callTimeout(30.seconds)
            .connectTimeout(15.seconds)
            .readTimeout(15.seconds)
            .addInterceptor(RetryInterceptor())
            .apply {
                additionalInterceptors.forEach(::addInterceptor)
                if (enableLogging) {
                    addInterceptor(
                        HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC },
                    )
                }
                if (metricsSink != null) {
                    eventListenerFactory(TelemetryEventListenerFactory(metricsSink))
                }
            }.build()

    fun retrofit(
        baseUrl: String,
        client: OkHttpClient,
        json: Json = defaultJson,
    ): Retrofit =
        Retrofit
            .Builder()
            .baseUrl(baseUrl)
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
}
