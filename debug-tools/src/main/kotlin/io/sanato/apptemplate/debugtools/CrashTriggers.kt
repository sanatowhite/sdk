package io.sanato.apptemplate.debugtools

/** 一键触发,用来验证 :core-telemetry 的崩溃/ANR 采集链路真的在跑。仅 debug 可达。 */
object CrashTriggers {
    fun triggerCrash(): Nothing = throw RuntimeException("Debug Drawer: manual crash trigger")

    /** 阻塞主线程足够久以触发系统 ANR 对话框——真机验证用,模拟器上系统可能不弹。 */
    fun triggerAnr() {
        Thread.sleep(ANR_BLOCK_MILLIS)
    }

    /** 持续分配大对象直到 OOM——用来验证内存采集在真正内存紧张时是否被触发。 */
    fun triggerOom(): Nothing {
        val leaks = mutableListOf<ByteArray>()
        while (true) {
            leaks.add(ByteArray(OOM_CHUNK_BYTES))
        }
    }

    private const val ANR_BLOCK_MILLIS = 15_000L
    private const val OOM_CHUNK_BYTES = 10 * 1024 * 1024
}
