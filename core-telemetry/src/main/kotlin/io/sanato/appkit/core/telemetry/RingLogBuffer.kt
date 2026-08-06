package io.sanato.appkit.core.telemetry

import java.util.concurrent.ConcurrentLinkedDeque

/**
 * 有界环形日志——不是给云端上报用的,是给反馈页"附带最近日志"这个功能用的
 * 本地快照,不需要持久化、不需要多进程共享。实现 [Telemetry] 本身,这样把它
 * 当作 Composite 的一个后端贡献进去(`@IntoSet`)就能自动捕获所有事件,不需要
 * 额外接一遍。
 */
class RingLogBuffer(
    private val capacity: Int = 200,
) : Telemetry {
    private val lines = ConcurrentLinkedDeque<String>()

    override val isEnabled = true

    fun snapshot(): List<String> = lines.toList()

    private fun push(line: String) {
        lines.addLast(line)
        while (lines.size > capacity) lines.pollFirst()
    }

    override fun event(
        name: String,
        params: Map<String, Any?>,
    ) = push("event: $name $params")

    override fun screenView(screenName: String) = push("screenView: $screenName")

    override fun startup(report: StartupReport) = push("startup: $report")

    override fun frame(report: FrameReport) = push("frame: $report")

    override fun networkRequest(report: NetworkRequestReport) = push("networkRequest: $report")

    override fun crash(
        throwable: Throwable,
        fatal: Boolean,
    ) = push("crash(fatal=$fatal): ${throwable.message}")

    override fun anr(report: AnrReport) = push("anr: $report")
}
