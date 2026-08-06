package io.sanato.appkit.core.telemetry.jank

import android.view.Window
import androidx.metrics.performance.JankStats
import androidx.metrics.performance.PerformanceMetricsState
import io.sanato.appkit.core.telemetry.FrameReport
import io.sanato.appkit.core.telemetry.Telemetry

/**
 * 每个 Activity/Window 一个实例。按屏幕聚合 total/janky 帧数,不逐帧上报——
 * 屏幕归属用 JankStats 自带的 [PerformanceMetricsState] 打标签("Screen" state),
 * 而不是外部维护一个"当前屏幕名"变量:后者在屏幕切换的那一帧容易把归属算错,
 * 前者是 JankStats 官方推荐的按屏幕拆分方式,每帧的 `FrameData.states` 自己
 * 带着当时生效的屏幕名。
 */
class ScreenJankReporter(
    window: Window,
    private val telemetry: Telemetry,
) {
    private val totalFramesByScreen = mutableMapOf<String, Long>()
    private val jankyFramesByScreen = mutableMapOf<String, Long>()
    private val stateHolder = PerformanceMetricsState.getHolderForHierarchy(window.decorView)

    private val jankStats =
        JankStats.createAndTrack(window) { frameData ->
            val screenName = frameData.states.firstOrNull { it.key == SCREEN_STATE_KEY }?.value ?: UNKNOWN_SCREEN
            totalFramesByScreen[screenName] = (totalFramesByScreen[screenName] ?: 0L) + 1L
            if (frameData.isJank) {
                jankyFramesByScreen[screenName] = (jankyFramesByScreen[screenName] ?: 0L) + 1L
            }
        }

    fun onScreenChanged(screenName: String) {
        stateHolder.state?.putState(SCREEN_STATE_KEY, screenName)
    }

    fun setTrackingEnabled(enabled: Boolean) {
        jankStats.isTrackingEnabled = enabled
    }

    /** 定期(比如 Activity.onPause)调用,把累计结果逐屏幕上报并清零。 */
    fun flush() {
        totalFramesByScreen.forEach { (screenName, total) ->
            val janky = jankyFramesByScreen[screenName] ?: 0L
            telemetry.frame(
                FrameReport(
                    screenName = screenName,
                    totalFrames = total,
                    jankyFrames = janky,
                    jankyFrameRatio = janky.toFloat() / total.toFloat(),
                ),
            )
        }
        totalFramesByScreen.clear()
        jankyFramesByScreen.clear()
    }

    private companion object {
        const val SCREEN_STATE_KEY = "Screen"
        const val UNKNOWN_SCREEN = "unknown"
    }
}
