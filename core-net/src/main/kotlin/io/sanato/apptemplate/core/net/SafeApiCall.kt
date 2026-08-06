package io.sanato.apptemplate.core.net

import io.sanato.apptemplate.core.common.AppResult
import kotlinx.serialization.SerializationException
import retrofit2.HttpException
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException
import kotlin.coroutines.cancellation.CancellationException

/**
 * Retrofit 3 的 suspend 函数直接抛异常——没有内置 Result,官方也没有 Result
 * CallAdapter。用这个包装而不是自定义 CallAdapter:CallAdapter 把错误处理藏进
 * Retrofit 内部,Repository 层更难测,而且等于逆着 Retrofit 3 的官方契约走。
 */
suspend inline fun <T> safeApiCall(crossinline block: suspend () -> T): AppResult<T> =
    try {
        AppResult.Success(block())
    } catch (e: CancellationException) {
        throw e // 绝不吞取消
    } catch (e: HttpException) {
        val body = runCatching { e.response()?.errorBody()?.string() }.getOrNull()
        AppResult.Failure(AppError.Http(e.code(), body))
    } catch (e: SocketTimeoutException) {
        AppResult.Failure(AppError.Timeout(e))
    } catch (e: UnknownHostException) {
        AppResult.Failure(AppError.NoConnectivity(e))
    } catch (e: SSLException) {
        AppResult.Failure(AppError.Ssl(e))
    } catch (e: SerializationException) {
        AppResult.Failure(AppError.Serialization(e))
    } catch (e: IOException) {
        AppResult.Failure(AppError.NoConnectivity(e))
    } catch (e: Throwable) {
        AppResult.Failure(AppError.Unknown(e))
    }
