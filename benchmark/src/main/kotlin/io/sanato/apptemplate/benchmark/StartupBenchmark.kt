package io.sanato.apptemplate.benchmark

import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

private const val TARGET_PACKAGE = "io.sanato.apptemplate"

/**
 * 冒烟性质,不是性能门禁——共享 vCPU 的 CI 跑出来的绝对数值方差常年 >20%,
 * 不可信也不该拿来做时序断言。这里只保证代码不腐烂 + Baseline Profile 确实生效
 * (`CompilationMode.Partial(BaselineProfileMode.Require)` 在 profile 未安装时会 fail,
 * 这是唯一机器能可靠断言的事)。真实数字看锁频物理设备上的人工测量。
 */
@LargeTest
@RunWith(AndroidJUnit4::class)
class StartupBenchmark {
    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    @Test
    fun startupCompilationNone() = startup(CompilationMode.None())

    @Test
    fun startupCompilationBaselineProfile() =
        startup(CompilationMode.Partial(baselineProfileMode = androidx.benchmark.macro.BaselineProfileMode.Require))

    private fun startup(compilationMode: CompilationMode) =
        benchmarkRule.measureRepeated(
            packageName = TARGET_PACKAGE,
            metrics = listOf(StartupTimingMetric()),
            iterations = 1,
            startupMode = StartupMode.COLD,
            compilationMode = compilationMode,
        ) {
            pressHome()
            startActivityAndWait()
        }
}
