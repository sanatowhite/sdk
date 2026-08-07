package io.sanato.appkit.telemetry.firebase

import android.os.Bundle
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.crashlytics.FirebaseCrashlytics
import io.sanato.appkit.core.telemetry.AnrReport
import io.sanato.appkit.core.telemetry.FrameReport
import io.sanato.appkit.core.telemetry.NetworkRequestReport
import io.sanato.appkit.core.telemetry.StartupReport
import io.sanato.appkit.core.telemetry.Telemetry

/**
 * 只用 Analytics + Crashlytics——Firebase Performance Monitoring 有意不接入:
 * `:core-telemetry` 已经自己采集了启动/帧/网络耗时,再叠一层 Firebase Perf
 * 纯属重复,还多引入一个 Gradle 插件依赖。
 */
class FirebaseTelemetry(
    private val analytics: FirebaseAnalytics,
    private val crashlytics: FirebaseCrashlytics,
) : Telemetry {
    override val isEnabled = true

    override fun event(
        name: String,
        params: Map<String, Any?>,
    ) {
        analytics.logEvent(name, params.toBundle())
    }

    override fun screenView(screenName: String) {
        val bundle = Bundle().apply { putString(FirebaseAnalytics.Param.SCREEN_NAME, screenName) }
        analytics.logEvent(FirebaseAnalytics.Event.SCREEN_VIEW, bundle)
    }

    override fun startup(report: StartupReport) {
        event(
            "app_startup",
            mapOf(
                "launch_type" to report.launchType.name,
                "ttid_ms" to report.timeToInitialDisplayMillis,
                "ttfd_ms" to report.timeToFullDisplayMillis,
            ),
        )
    }

    override fun frame(report: FrameReport) {
        event(
            "screen_jank",
            mapOf(
                "screen_name" to report.screenName,
                "total_frames" to report.totalFrames,
                "janky_frames" to report.jankyFrames,
                "janky_ratio" to report.jankyFrameRatio,
            ),
        )
    }

    override fun networkRequest(report: NetworkRequestReport) {
        event(
            "network_request",
            mapOf(
                "route" to report.routeTemplate,
                "method" to report.method,
                "status" to report.httpStatus,
                "duration_ms" to report.totalMillis,
                "failed" to report.failed,
            ),
        )
    }

    /**
     * fatal/non-fatal 都调同一个 `recordException` API——真正的 fatal 崩溃上报
     * 由 Crashlytics 自己的默认 handler 负责,这里(经由我们自己的
     * `CrashRecorder` 链路,延后一次启动才调用)记录的是"已经发生过的崩溃事后
     * 补报",用 `log` 附带 fatal 标记方便在 Crashlytics 后台区分来源。
     */
    override fun crash(
        throwable: Throwable,
        fatal: Boolean,
    ) {
        crashlytics.log("fatal=$fatal")
        crashlytics.recordException(throwable)
    }

    override fun anr(report: AnrReport) {
        crashlytics.log("anr_suspected source=${report.source}")
        crashlytics.recordException(IllegalStateException("Suspected ANR: ${report.source}"))
    }

    private fun Map<String, Any?>.toBundle(): Bundle =
        Bundle().apply {
            forEach { (key, value) ->
                when (value) {
                    is String -> putString(key, value)
                    is Int -> putInt(key, value)
                    is Long -> putLong(key, value)
                    is Double -> putDouble(key, value)
                    is Float -> putFloat(key, value)
                    is Boolean -> putBoolean(key, value)
                    null -> Unit
                    else -> putString(key, value.toString())
                }
            }
        }
}
