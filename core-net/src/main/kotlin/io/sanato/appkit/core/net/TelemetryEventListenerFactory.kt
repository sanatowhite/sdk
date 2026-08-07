package io.sanato.appkit.core.net

import okhttp3.Call
import okhttp3.EventListener
import okhttp3.Request
import okhttp3.Response
import retrofit2.Invocation
import java.io.IOException

/**
 * per-call Factory,无并发无泄漏(OkHttp 对每个 Call 都会调一次 [create])。
 * URL 走路由模板化——不模板化会导致指标基数爆炸。优先读 Retrofit 挂的
 * `Invocation` tag 拿精确模板,零猜测零 PII;拿不到(比如手写的裸 OkHttp 请求)
 * 就退回 `request.url.encodedPath`。
 */
class TelemetryEventListenerFactory(
    private val sink: NetworkMetricsSink,
) : EventListener.Factory {
    override fun create(call: Call): EventListener = TelemetryEventListener(call.request(), sink)
}

private class TelemetryEventListener(
    private val request: Request,
    private val sink: NetworkMetricsSink,
) : EventListener() {
    private var callStartNanos = 0L
    private var lastHttpStatus: Int? = null

    override fun callStart(call: Call) {
        callStartNanos = System.nanoTime()
    }

    override fun responseHeadersEnd(
        call: Call,
        response: Response,
    ) {
        lastHttpStatus = response.code
    }

    override fun callEnd(call: Call) {
        report(failed = false)
    }

    override fun callFailed(
        call: Call,
        ioe: IOException,
    ) {
        report(failed = true)
    }

    private fun report(failed: Boolean) {
        val totalMillis = (System.nanoTime() - callStartNanos) / 1_000_000
        sink.onRequestCompleted(
            routeTemplate = routeTemplateOf(),
            method = request.method,
            httpStatus = lastHttpStatus,
            totalMillis = totalMillis,
            failed = failed,
        )
    }

    private fun routeTemplateOf(): String {
        val invocation = request.tag(Invocation::class.java)
        return invocation?.method()?.name ?: request.url.encodedPath
    }
}
