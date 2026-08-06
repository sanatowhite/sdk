package io.sanato.appkit.core.net

import io.sanato.appkit.core.common.AppResult
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
 *
 * 刻意【不】用 `inline`:inline 函数体会被复制进每个调用点的字节码,这里
 * catch 的具体异常类型(HttpException/SerializationException 等)就会永久
 * 锁进消费方的 ABI——将来给它加一个 catch 分支就变成一次破坏性 API 变更。
 * 一次挂起 lambda 的对象分配在网络请求的尺度上不可测,inline 唯一的收益
 * 换不回这个代价。
 */
suspend fun <T> safeApiCall(block: suspend () -> T): AppResult<T> =
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
