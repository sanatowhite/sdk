package io.sanato.logkit

import android.app.Application
import android.content.Context
import java.io.File

/**
 * 公开入口。整个模块只有这一个 `object`——没有单例注入、没有 DI 注解,
 * 因为它必须能在 `Application.attachBaseContext` 里被安装,那时候宿主的
 * DI 图(Hilt/Dagger/……)还不存在。
 *
 * 语义契约(这段文字本身就是 API 的一部分,写进 logkit/README.md):
 *  - [install] 已安装过返回 `false`,否则 `true`。永不抛异常。
 *  - `install` 之前调用 [v]/[d]/[i]/[w]/[e] 都是静默 no-op——不崩溃、不分配。
 *  - [flushBlocking] 返回 `true` 当且仅当调用之前被接受的每一条记录都已经
 *    落盘并 `fsync`。超时或 SDK 处于降级状态时返回 `false`。`install` 之前
 *    调用返回 `true`(空真)。与调用【同时】发生的 enqueue 可能被包含,也
 *    可能不被包含。
 *  - [fatal] = 以 ERROR 级别写入 + 自动 [flushBlocking];返回 flush 的结果。
 *  - [export] 会做到 ~5MB 的磁盘 IO——不要在主线程调用。
 *  - 没有 `shutdown()`:Android 进程里没有正确的关闭时机,而这是一份永久
 *    只增不减的公开 API,一个 `shutdown()` 的设计错误就是永久的。持久性
 *    靠 [flushBlocking],不靠"关闭"。
 */
public object LogKit {
    @Volatile private var core: LogKitCore? = null
    private val installLock = Any()

    @JvmStatic
    public fun install(
        context: Context,
        config: LogKitConfig,
    ): Boolean {
        synchronized(installLock) {
            if (core != null) {
                core?.enqueue(LogLevel.WARN, "LogKit", "duplicate install() ignored", null)
                return false
            }
            return try {
                val application = context.applicationContext as Application
                val newCore = LogKitCore(application, config)
                core = newCore
                newCore.start()
                true
            } catch (t: Throwable) {
                // install() 永不抛——哪怕 applicationContext 不是 Application 这种
                // 几乎不会发生的宿主异常,也只是"这次没装成",不能把 attachBaseContext
                // 变成一次启动崩溃。
                android.util.Log.e("LogKit", "install() failed, logging disabled", t)
                false
            }
        }
    }

    @JvmStatic
    public fun isInstalled(): Boolean = core != null

    @JvmStatic
    public fun isHealthy(): Boolean = core?.isHealthy() ?: false

    @JvmStatic
    public fun v(
        tag: String,
        message: String,
    ) {
        core?.enqueue(LogLevel.VERBOSE, tag, message, null)
    }

    @JvmStatic
    public fun d(
        tag: String,
        message: String,
    ) {
        core?.enqueue(LogLevel.DEBUG, tag, message, null)
    }

    @JvmStatic
    public fun i(
        tag: String,
        message: String,
    ) {
        core?.enqueue(LogLevel.INFO, tag, message, null)
    }

    @JvmStatic
    public fun w(
        tag: String,
        message: String,
    ) {
        core?.enqueue(LogLevel.WARN, tag, message, null)
    }

    @JvmStatic
    public fun e(
        tag: String,
        message: String,
    ) {
        core?.enqueue(LogLevel.ERROR, tag, message, null)
    }

    @JvmStatic
    public fun e(
        tag: String,
        message: String,
        throwable: Throwable,
    ) {
        core?.enqueue(LogLevel.ERROR, tag, message, throwable)
    }

    @JvmStatic
    public fun fatal(
        tag: String,
        message: String,
        throwable: Throwable?,
    ): Boolean = core?.fatal(tag, message, throwable) ?: true

    @JvmStatic
    public fun flushBlocking(timeoutMillis: Long): Boolean = core?.flushBlocking(timeoutMillis) ?: true

    @JvmStatic
    public fun droppedRecordCount(): Long = core?.droppedRecordCount() ?: 0L

    @JvmStatic
    public fun stats(): LogKitStats = core?.stats() ?: emptyStats()

    private fun emptyStats(): LogKitStats =
        LogKitStats(
            files = emptyList(),
            totalBytes = 0L,
            budgetBytes = 0L,
            queuedRecords = 0,
            nextSequence = 0L,
            droppedRecords = 0L,
            evictedFiles = 0L,
            persistenceHealthy = false,
            keyId = 0,
            formatVersion = io.sanato.logkit.format.FileHeaderCodec.FORMAT_VERSION,
        )

    @JvmStatic
    public fun purge(): Int = core?.purge() ?: 0

    @JvmStatic
    public fun export(destination: File): Boolean = core?.export(destination) ?: false

    internal fun installForTest(testCore: LogKitCore) {
        core = testCore
    }

    internal fun resetForTest() {
        core = null
    }
}
