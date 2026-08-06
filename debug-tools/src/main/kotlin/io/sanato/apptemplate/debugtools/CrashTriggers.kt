package io.sanato.apptemplate.debugtools

import io.sanato.logkit.LogKit

/**
 * 一键触发,用来验证 :core-telemetry 的崩溃/ANR 采集链路真的在跑。仅 debug 可达。
 * 每次触发前先记一条 WARN——解密后能看到"触发 → FATAL/ANR trace"这条因果,
 * 而不是一份孤零零看不出为什么会崩的日志。
 */
object CrashTriggers {
    fun triggerCrash(): Nothing {
        LogKit.w("CrashTriggers", "trigger: crash")
        throw RuntimeException("Debug Drawer: manual crash trigger")
    }

    /** 阻塞主线程足够久以触发系统 ANR 对话框——真机验证用,模拟器上系统可能不弹。 */
    fun triggerAnr() {
        LogKit.w("CrashTriggers", "trigger: anr (about to block main thread for ${ANR_BLOCK_MILLIS}ms)")
        Thread.sleep(ANR_BLOCK_MILLIS)
    }

    /** 持续分配大对象直到 OOM——用来验证内存采集在真正内存紧张时是否被触发。 */
    fun triggerOom(): Nothing {
        LogKit.w("CrashTriggers", "trigger: oom")
        val leaks = mutableListOf<ByteArray>()
        while (true) {
            leaks.add(ByteArray(OOM_CHUNK_BYTES))
        }
    }

    private const val ANR_BLOCK_MILLIS = 15_000L
    private const val OOM_CHUNK_BYTES = 10 * 1024 * 1024
}
