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
        private val logSink: DiagnosticLogSink,
    ) : AppInitializer {
        override fun init(application: Application) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                // `reaper` 提成局部变量,让下面两个调用共用同一个实例——
                // `reapNewAnrExits()` 是消耗性的,每次启动只能有一个调用方
                // (见其文档),但 `readAnrTrace` 是无状态的纯读,共用实例没问题。
                val reaper = AnrExitInfoReaper(application)
                val newAnrs = reaper.reapNewAnrExits()
                repeat(newAnrs.size) { telemetry.anr(AnrReport(AnrSource.EXIT_INFO)) }

                if (newAnrs.isNotEmpty()) {
                    // ANR trace 常常几十到几百 KB,读取是磁盘 IO——这个初始化器
                    // 是 @Eager,跑在 Application.onCreate 主线程,绝不能在这里
                    // 内联读,否则每次"上次发生过 ANR"之后的启动都会被拖慢,
                    // 用被测之物污染冷启动测量(同一条理由,见 CLAUDE.md 里
                    // androidx.startup 被否决的原因)。
                    Thread {
                        newAnrs.forEach { info ->
                            reaper.readAnrTrace(info)?.let { trace ->
                                logSink.log(
                                    DiagnosticLevel.WARN,
                                    "ANR",
                                    "exitInfo ts=${info.timestamp} pid=${info.pid}\n$trace",
                                    null,
                                )
                            }
                        }
                    }.apply {
                        name = "logkit-anr-trace"
                        priority = Thread.MIN_PRIORITY
                    }.start()
                }
            } else {
                val beacon = ForegroundExitBeacon(application)
                if (beacon.consumePreviousSessionAbnormalExit()) {
                    telemetry.anr(AnrReport(AnrSource.FOREGROUND_BEACON))
                    logSink.log(
                        DiagnosticLevel.WARN,
                        "ANR",
                        "previous session died in foreground (no trace available pre-API 30)",
                        null,
                    )
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
