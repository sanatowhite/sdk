package io.sanato.appkit.core.telemetry

import android.app.Application
import android.os.Build
import io.sanato.appkit.core.init.AppInitializer
import io.sanato.appkit.core.telemetry.anr.AnrExitInfoReaper
import io.sanato.appkit.core.telemetry.anr.ForegroundExitBeacon
import io.sanato.appkit.core.telemetry.crash.CrashRecorder
import io.sanato.appkit.core.telemetry.memory.MemorySampler
import io.sanato.appkit.core.telemetry.startup.StartupTracker
import javax.inject.Inject

/**
 * 只用 `javax.inject.Inject` 构造器标注，不依赖 Hilt——不用 Hilt 的消费方可以
 * 直接 `AppInitializers(eagerInitializers = setOf(StartupTrackerInitializer(...), ...))`
 * 手动构造，Hilt 消费方通过 `:core-telemetry-hilt` 的 `@Binds @IntoSet` 绑定注入。
 *
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
 * [MemorySampler] 本身是 Hilt `@Singleton`(见 `:core-telemetry-hilt` 的
 * `TelemetryModule`),这里只是触发它 `start()`;消费方的 Activity 之后还会用
 * 同一个实例调用 [MemorySampler.sampleOnColdStartComplete]。
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
