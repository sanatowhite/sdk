package io.sanato.appkit.core.telemetry

/**
 * 会话级采样,不是逐事件随机——同一次会话内的行为要么全被采样要么全不被,
 * 否则漏斗类分析(同一会话内 A 事件被采样但 B 事件没被采样)会直接失真。
 *
 * `randomValue` 由调用方传入(而不是内部调 `Math.random()`)纯粹是为了可测——
 * 生产环境调用方传 `Random.nextFloat()` 即可。
 */
class SamplingTelemetry(
    private val delegate: Telemetry,
    sampleRate: Float,
    randomValue: Float,
) : Telemetry {
    private val sessionSampledIn: Boolean = randomValue < sampleRate.coerceIn(0f, 1f)

    override val isEnabled: Boolean get() = sessionSampledIn && delegate.isEnabled

    private inline fun ifSampledIn(action: () -> Unit) {
        if (sessionSampledIn) action()
    }

    override fun event(
        name: String,
        params: Map<String, Any?>,
    ) = ifSampledIn { delegate.event(name, params) }

    override fun screenView(screenName: String) = ifSampledIn { delegate.screenView(screenName) }

    override fun startup(report: StartupReport) = ifSampledIn { delegate.startup(report) }

    override fun frame(report: FrameReport) = ifSampledIn { delegate.frame(report) }

    override fun networkRequest(report: NetworkRequestReport) = ifSampledIn { delegate.networkRequest(report) }

    // 崩溃永远不采样——采样掉崩溃报告等于系统性低估崩溃率,这条没有例外。
    override fun crash(
        throwable: Throwable,
        fatal: Boolean,
    ) = delegate.crash(throwable, fatal)

    override fun anr(report: AnrReport) = ifSampledIn { delegate.anr(report) }
}
