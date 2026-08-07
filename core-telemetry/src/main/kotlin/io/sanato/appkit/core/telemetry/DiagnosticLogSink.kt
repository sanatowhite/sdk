package io.sanato.appkit.core.telemetry

/**
 * 严重级别归本模块所有——不引用 `:logkit` 的任何类型,桥接层(`:app`)负责映射。
 * 这五个常量对应 `LogKit.v/d/i/w/e` 加一个"写入后立即同步落盘"的 FATAL。
 */
enum class DiagnosticLevel { DEBUG, INFO, WARN, ERROR, FATAL }

/**
 * 诊断日志的落点接口——和 `:core-net` 的 `NetworkMetricsSink` 同一模式:
 * 归 `:core-telemetry` 所有,这样本模块完全不需要依赖 `:logkit`
 * (`verifyModuleGraph` 里 `:core-telemetry → setOf(":core-common")` 不变)。
 * `:app` 提供桥接实现连接两边。
 *
 * 只在两个 `Telemetry` 结构上覆盖不到的地方使用:① 崩溃处理器(见
 * [io.sanato.appkit.core.telemetry.crash.CrashRecorder]——它跑在
 * `attachBaseContext`,Hilt 还不存在,且本模块的规则是崩溃处理器内绝不调用
 * `Telemetry` 后端);② ANR 现场的主线程栈字节(见
 * [io.sanato.appkit.core.telemetry.anr.AnrExitInfoReaper.readAnrTrace],
 * `Telemetry.anr()` 从没见过这些字节)。除此之外的信号(startup/frame/
 * networkRequest/screenView/普通 crash/anr 计数)都通过把 `Telemetry` 实现
 * 绑成 `@IntoSet` 后端的方式免费获得,不需要走这个接口。
 *
 * [DiagnosticLevel.FATAL] 的语义由桥接方定义:实现应当写入后立即同步落盘。
 * 本模块不知道"落盘"这件事存在,也不知道"LogKit"是什么。
 */
fun interface DiagnosticLogSink {
    fun log(
        level: DiagnosticLevel,
        tag: String,
        message: String,
        throwable: Throwable?,
    )

    companion object {
        /** 独立复用 `:core-telemetry` 时的默认值——全部丢弃,零成本。 */
        val NoOp: DiagnosticLogSink = DiagnosticLogSink { _, _, _, _ -> }
    }
}
