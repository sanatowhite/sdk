package io.sanato.apptemplate.core.telemetry

/**
 * 混合形态:固定 schema 用强类型方法(startup/frame/networkRequest/crash/anr)+
 * `event()` 泛化逃生口 + `isEnabled` 保证关闭态下 `event()` 的调用方可以
 * 用 [eventIfEnabled] 完全跳过 params 构造,不产生任何分配。
 */
interface Telemetry {
    val isEnabled: Boolean

    fun event(
        name: String,
        params: Map<String, Any?> = emptyMap(),
    )

    fun screenView(screenName: String)

    fun startup(report: StartupReport)

    fun frame(report: FrameReport)

    fun networkRequest(report: NetworkRequestReport)

    /** 崩溃永远不受采样影响——各实现(尤其 [SamplingTelemetry])必须无条件转发。 */
    fun crash(
        throwable: Throwable,
        fatal: Boolean,
    )

    fun anr(report: AnrReport)
}

inline fun Telemetry.eventIfEnabled(
    name: String,
    params: () -> Map<String, Any?>,
) {
    if (isEnabled) event(name, params())
}

enum class LaunchType { COLD, WARM }

data class StartupReport(
    val launchType: LaunchType,
    val timeToInitialDisplayMillis: Long,
    val timeToFullDisplayMillis: Long? = null,
)

// 故意不暴露 androidx.metrics 的 FrameData——那会让所有 Telemetry 实现方
// 被迫依赖 JankStats。
data class FrameReport(
    val screenName: String,
    val totalFrames: Long,
    val jankyFrames: Long,
    val jankyFrameRatio: Float,
)

data class NetworkRequestReport(
    val routeTemplate: String,
    val method: String,
    val httpStatus: Int?,
    val totalMillis: Long,
    val failed: Boolean,
)

enum class AnrSource { EXIT_INFO, FOREGROUND_BEACON }

data class AnrReport(
    val source: AnrSource,
)
