package io.sanato.apptemplate.telemetry

import android.app.Application
import android.os.Build
import io.sanato.apptemplate.core.telemetry.AnrReport
import io.sanato.apptemplate.core.telemetry.AnrSource
import io.sanato.apptemplate.core.telemetry.AppForegroundState
import io.sanato.apptemplate.core.telemetry.Telemetry
import io.sanato.apptemplate.core.telemetry.anr.AnrExitInfoReaper
import io.sanato.apptemplate.core.telemetry.anr.ForegroundExitBeacon
import io.sanato.apptemplate.core.telemetry.crash.CrashRecorder
import io.sanato.apptemplate.core.telemetry.memory.MemorySampler
import io.sanato.apptemplate.core.telemetry.startup.StartupTracker
import io.sanato.apptemplate.init.AppInitializer
import javax.inject.Inject

/**
 * Eager——必须在第一个 Activity.onCreate 之前就注册好 ActivityLifecycleCallbacks,
 * 否则会错过要测量的那个 Activity 本身。
 */
class StartupTrackerInitializer
    @Inject
    constructor(
        private val telemetry: Telemetry,
    ) : AppInitializer {
        override fun init(application: Application) {
            StartupTracker(application, telemetry).start()
        }
    }

/**
 * Eager——上一次会话是否异常退出的判断越早做越好,前台/后台监听也需要从
 * 第一个 Activity 起就开始跟踪。
 */
class AnrCheckInitializer
    @Inject
    constructor(
        private val telemetry: Telemetry,
        private val foregroundState: AppForegroundState,
    ) : AppInitializer {
        override fun init(application: Application) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val newAnrs = AnrExitInfoReaper(application).reapNewAnrExits()
                repeat(newAnrs.size) { telemetry.anr(AnrReport(AnrSource.EXIT_INFO)) }
            } else {
                val beacon = ForegroundExitBeacon(application)
                if (beacon.consumePreviousSessionAbnormalExit()) {
                    telemetry.anr(AnrReport(AnrSource.FOREGROUND_BEACON))
                }
                foregroundState.addListener { isForeground ->
                    if (isForeground) beacon.markForeground() else beacon.markBackground()
                }
            }
        }
    }

/** Deferred——崩溃 handler 本身已在 `attachBaseContext` 装过,这里只上报上次崩溃遗留的文件。 */
class CrashReportingInitializer
    @Inject
    constructor(
        private val telemetry: Telemetry,
    ) : AppInitializer {
        override fun init(application: Application) {
            CrashRecorder.drainPendingCrashReports(application).forEach { report ->
                telemetry.crash(IllegalStateException(report), fatal = true)
            }
        }
    }

/**
 * Deferred——采样不需要在首帧前就绪,延后启动能避免给最敏感的启动窗口叠加开销。
 * [MemorySampler] 本身是 Hilt `@Singleton`(见 `TelemetryModule`),这里只是
 * 触发它 `start()`;`MainActivity` 之后还会用同一个实例调用
 * [MemorySampler.sampleOnColdStartComplete]。
 */
class MemorySamplerInitializer
    @Inject
    constructor(
        private val memorySampler: MemorySampler,
    ) : AppInitializer {
        override fun init(application: Application) {
            memorySampler.start()
        }
    }
