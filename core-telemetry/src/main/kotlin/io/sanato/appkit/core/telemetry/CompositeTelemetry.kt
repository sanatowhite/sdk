package io.sanato.appkit.core.telemetry

import android.util.Log

/** 故障隔离:一个后端抛异常不能拖垮其他后端,也不能拖垮调用方。 */
class CompositeTelemetry(
    private val backends: Set<Telemetry>,
) : Telemetry {
    override val isEnabled: Boolean = backends.any { it.isEnabled }

    private inline fun eachBackend(action: (Telemetry) -> Unit) {
        backends.forEach { backend ->
            runCatching { action(backend) }.onFailure { error ->
                // Log 本身在纯 JVM 单测里也是未 mock 的 Android 类,连日志这一步
                // 都不能让它反过来炸掉调用方——所以外面再包一层 runCatching。
                runCatching { Log.w("CompositeTelemetry", "backend ${backend::class.simpleName} failed", error) }
            }
        }
    }

    override fun event(
        name: String,
        params: Map<String, Any?>,
    ) = eachBackend { it.event(name, params) }

    override fun screenView(screenName: String) = eachBackend { it.screenView(screenName) }

    override fun startup(report: StartupReport) = eachBackend { it.startup(report) }

    override fun frame(report: FrameReport) = eachBackend { it.frame(report) }

    override fun networkRequest(report: NetworkRequestReport) = eachBackend { it.networkRequest(report) }

    override fun crash(
        throwable: Throwable,
        fatal: Boolean,
    ) = eachBackend { it.crash(throwable, fatal) }

    override fun anr(report: AnrReport) = eachBackend { it.anr(report) }
}
