package io.sanato.appkit.core.telemetry.anr

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.annotation.VisibleForTesting
import androidx.annotation.WorkerThread
import java.io.IOException
import java.io.InputStream

/**
 * API 30+ 用 `ApplicationExitInfo` 开机回捞,不需要看门狗或信标——系统自己记录了
 * 退出原因。去重用"上次观测 key 全集的覆盖式持久化":每次读取后,把【当前】
 * 系统返回的历史记录 key 全集整体覆盖写回,而不是不断往一个 set 里追加——
 * 系统本身只保留有限条历史,覆盖式持久化让本地存储量同样有界。
 *
 * key = timestamp+pid+reason+processName——不用纯 pid(会被系统复用),
 * 不用时间戳高水位(设备时钟可能倒退,高水位比较会漏报)。
 */
@RequiresApi(Build.VERSION_CODES.R)
class AnrExitInfoReaper(
    private val context: Context,
) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /**
     * ⚠️ 消耗性:每次读取都会用【当前】系统返回的历史记录 key 全集整体覆盖
     * `KEY_SEEN`。这意味着每次进程启动【只能有一个调用方】调这个方法——
     * 加第二条消费路径会让其中一个永远看到空表,而且不会有任何报错提示你
     * 加错了。ANR trace 的读取(见 [readAnrTrace])被设计成一个独立的、
     * 非消耗性的方法,正是为了不诱使有人在这里加第二个调用点。
     */
    fun reapNewAnrExits(): List<ApplicationExitInfo> {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val history = activityManager.getHistoricalProcessExitReasons(null, 0, 0)
        val anrs = history.filter { it.reason == ApplicationExitInfo.REASON_ANR }

        val seenKeys = prefs.getStringSet(KEY_SEEN, emptySet()).orEmpty()
        val currentKeys = anrs.map(::keyOf).toSet()
        val newOnes = anrs.filter { keyOf(it) !in seenKeys }

        prefs.edit().putStringSet(KEY_SEEN, currentKeys).apply()
        return newOnes
    }

    /**
     * ANR 现场的主线程栈——`traceInputStream()` 是磁盘 I/O,而且一份完整 trace
     * 动辄几十到几百 KB,**绝不能在主线程调用**(调用方通常是 `@Eager` 初始化器,
     * 跑在 `Application.onCreate` 里;把它挪到后台线程是调用方的责任,这里只是
     * 用 [WorkerThread] 标注约束)。也绝不能不截断:下游(`:logkit`)日志总预算
     * 常常只有几 MB,一份完整 trace 就足以把其余日志全部挤掉,所以默认按
     * [maxBytes] 硬截断并附加提示。
     *
     * 返回 null 表示系统没有为这条记录保存 trace(常见,不是错误)。
     */
    @WorkerThread
    fun readAnrTrace(
        info: ApplicationExitInfo,
        maxBytes: Int = DEFAULT_MAX_TRACE_BYTES,
    ): String? {
        val stream = info.traceInputStream ?: return null
        return try {
            readTruncated(stream, maxBytes)
        } catch (e: IOException) {
            null
        }
    }

    private fun keyOf(info: ApplicationExitInfo): String =
        "${info.timestamp}:${info.pid}:${info.reason}:${info.processName}"

    companion object {
        private const val PREFS_NAME = "telemetry_anr_reaper"
        private const val KEY_SEEN = "seen_anr_keys"
        private const val DEFAULT_MAX_TRACE_BYTES = 64 * 1024

        /**
         * 拆成独立函数只是为了能在不依赖 `ApplicationExitInfo`(Robolectric 没有
         * 现成 shadow 能构造带自定义 `traceInputStream` 的实例)的前提下,单独
         * 测试"读取 + 截断 + 附加提示"这段逻辑——见 `AnrTraceTruncationTest`。
         */
        @VisibleForTesting
        internal fun readTruncated(
            input: InputStream,
            maxBytes: Int,
        ): String =
            input.use { stream ->
                // 手写读取循环而不是 InputStream.readNBytes()——后者是 JDK 9+
                // 才有的默认方法,minSdk 24 上能不能靠 core library desugaring
                // 覆盖到不确定,不值得为省几行代码冒这个险。
                val buffer = ByteArray(maxBytes)
                var totalRead = 0
                while (totalRead < maxBytes) {
                    val n = stream.read(buffer, totalRead, maxBytes - totalRead)
                    if (n < 0) break
                    totalRead += n
                }
                val hasMore = totalRead == maxBytes && stream.read() != -1
                val text = String(buffer, 0, totalRead, Charsets.UTF_8)
                if (hasMore) "$text\n…truncated at $maxBytes bytes…" else text
            }
    }
}
