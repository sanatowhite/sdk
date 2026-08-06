package io.sanato.apptemplate.core.net

import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import kotlin.math.min
import kotlin.math.pow

/**
 * 5xx 指数退避,429 读 `Retry-After`,非幂等请求(POST/PATCH)不重试——重试一个
 * 已经"部分执行"的 POST 有制造重复副作用的风险,不能一律重试。
 */
class RetryInterceptor(
    private val maxRetries: Int = 3,
    private val baseDelayMillis: Long = 500,
    private val maxDelayMillis: Long = 8_000,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        var response = chain.proceed(request)
        var attempt = 0

        while (attempt < maxRetries && isRetryable(request, response)) {
            val delayMillis = retryAfterMillis(response) ?: exponentialBackoffMillis(attempt)
            response.close()
            Thread.sleep(delayMillis)
            attempt++
            response = chain.proceed(request)
        }
        return response
    }

    private fun isRetryable(
        request: Request,
        response: Response,
    ): Boolean {
        if (!isIdempotent(request)) return false
        return response.code in 500..599 || response.code == 429
    }

    private fun isIdempotent(request: Request): Boolean = request.method in IDEMPOTENT_METHODS

    private fun retryAfterMillis(response: Response): Long? {
        if (response.code != 429) return null
        return response.header("Retry-After")?.toLongOrNull()?.times(1000)
    }

    private fun exponentialBackoffMillis(attempt: Int): Long =
        min(baseDelayMillis * 2.0.pow(attempt).toLong(), maxDelayMillis)

    private companion object {
        val IDEMPOTENT_METHODS = setOf("GET", "HEAD", "OPTIONS", "PUT", "DELETE")
    }
}
