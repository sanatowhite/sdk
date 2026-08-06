package io.sanato.appkit.core.telemetry.memory

import android.app.Application
import android.content.ComponentCallbacks2
import android.content.res.Configuration
import android.os.Debug
import android.os.Handler
import android.os.Looper
import io.sanato.appkit.core.telemetry.AppForegroundState
import io.sanato.appkit.core.telemetry.Telemetry

/**
 * 廉价档:`Runtime.*` 前台每 30s 采一次,几乎零开销,只在前台运行(后台停止)。
 * 昂贵档:`Debug.MemoryInfo` 只在明确的几个时刻采——绝不定时采,
 * `getProcessMemoryInfo` 很贵,且 API 29+ 起被系统限流 5 分钟一次。
 */
class MemorySampler(
    private val application: Application,
    private val foregroundState: AppForegroundState,
    private val telemetry: Telemetry,
) {
    private val handler = Handler(Looper.getMainLooper())

    private val cheapSamplingRunnable =
        object : Runnable {
            override fun run() {
                sampleCheap()
                if (foregroundState.isForeground) {
                    handler.postDelayed(this, CHEAP_SAMPLE_INTERVAL_MILLIS)
                }
            }
        }

    fun start() {
        foregroundState.addListener { isForeground ->
            if (isForeground) {
                handler.post(cheapSamplingRunnable)
            } else {
                handler.removeCallbacks(cheapSamplingRunnable)
                sampleExpensive(MOMENT_BACKGROUNDED)
            }
        }

        application.registerComponentCallbacks(
            object : ComponentCallbacks2 {
                override fun onConfigurationChanged(newConfig: Configuration) = Unit

                @Deprecated("Deprecated in Java, still the only low-memory signal on old API levels")
                override fun onLowMemory() = sampleExpensive(MOMENT_LOW_MEMORY)

                override fun onTrimMemory(level: Int) {
                    if (level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL) {
                        sampleExpensive(MOMENT_TRIM_CRITICAL)
                    }
                }
            },
        )
    }

    /** 冷启动完成(TTFD 之后)是明确要采的第三个时刻,由 :app 在合适的时机调用。 */
    fun sampleOnColdStartComplete() = sampleExpensive(MOMENT_COLD_START_COMPLETE)

    private fun sampleCheap() {
        val runtime = Runtime.getRuntime()
        telemetry.event(
            EVENT_CHEAP_SAMPLE,
            mapOf(
                "used_bytes" to (runtime.totalMemory() - runtime.freeMemory()),
                "max_bytes" to runtime.maxMemory(),
            ),
        )
    }

    private fun sampleExpensive(momentTag: String) {
        val info = Debug.MemoryInfo()
        Debug.getMemoryInfo(info)
        telemetry.event(
            EVENT_EXPENSIVE_SAMPLE,
            mapOf(
                "moment" to momentTag,
                "total_pss_kb" to info.totalPss,
            ),
        )
    }

    private companion object {
        const val CHEAP_SAMPLE_INTERVAL_MILLIS = 30_000L
        const val EVENT_CHEAP_SAMPLE = "memory_cheap_sample"
        const val EVENT_EXPENSIVE_SAMPLE = "memory_expensive_sample"
        const val MOMENT_COLD_START_COMPLETE = "cold_start_complete"
        const val MOMENT_BACKGROUNDED = "app_backgrounded"
        const val MOMENT_LOW_MEMORY = "low_memory"
        const val MOMENT_TRIM_CRITICAL = "trim_memory_critical"
    }
}
