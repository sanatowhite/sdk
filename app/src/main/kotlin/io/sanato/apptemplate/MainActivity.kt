package io.sanato.apptemplate

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.AndroidEntryPoint
import io.sanato.appkit.core.telemetry.RingLogBuffer
import io.sanato.appkit.core.telemetry.Telemetry
import io.sanato.appkit.core.telemetry.jank.ScreenJankReporter
import io.sanato.appkit.core.telemetry.memory.MemorySampler
import io.sanato.appkit.core.ui.theme.AppTemplateTheme
import io.sanato.appkit.feature.feedback.FeedbackScreenshotHost
import io.sanato.appkit.feature.settings.AppEntryViewModel
import io.sanato.appkit.feature.settings.ConsentRoute
import io.sanato.apptemplate.debug.DebugOverlay
import io.sanato.apptemplate.navigation.AppNavHost
import io.sanato.apptemplate.navigation.Home
import javax.inject.Inject

/**
 * `AppCompatActivity`(不是 `ComponentActivity`)——应用内语言切换
 * (`AppCompatDelegate.setApplicationLocales`,Phase 7)在 pre-T 设备上的兼容
 * 实现需要 AppCompat 的 Activity 基类。
 */
@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    @Inject
    lateinit var telemetry: Telemetry

    @Inject
    lateinit var memorySampler: MemorySampler

    @Inject
    lateinit var ringLogBuffer: RingLogBuffer

    private lateinit var jankReporter: ScreenJankReporter

    private val entryViewModel: AppEntryViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        // 必须在 super.onCreate() 之前调用,core-splashscreen 才能接管
        // Theme.AppTemplate.Starting -> Theme.AppTemplate 的主题切换时机。
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)

        // 首启同意的条件 startDestination 要等 DataStore 读出来才能确定——
        // splash 一直留在屏幕上直到 AppEntryViewModel 算出结果(带 1.5s 超时,
        // 见该 ViewModel 的说明,防止 DataStore 异常时永久卡住)。
        splashScreen.setKeepOnScreenCondition { entryViewModel.consentRequired.value == null }

        // targetSdk 36+ 起无法退出 edge-to-edge,这里无条件调用一次而不是判断版本——
        // 同时覆盖了 24-35 仍需要显式开启的情况。
        enableEdgeToEdge()

        jankReporter = ScreenJankReporter(window, telemetry)
        jankReporter.onScreenChanged("Home")

        setContent {
            val darkTheme = isSystemInDarkTheme()
            SideEffect {
                WindowCompat.getInsetsController(window, window.decorView).apply {
                    isAppearanceLightStatusBars = !darkTheme
                    isAppearanceLightNavigationBars = !darkTheme
                }
            }
            val consentRequired by entryViewModel.consentRequired.collectAsStateWithLifecycle()
            val resolvedStartDestination = consentRequired?.let { if (it) ConsentRoute else Home }
            if (resolvedStartDestination != null) {
                AppTemplateTheme(darkTheme = darkTheme) {
                    // FeedbackScreenshotHost 包住内容根节点,给 :feature-feedback 的
                    // "附带截图"功能提供捕获点——不用自己摆弄 GraphicsLayer。
                    FeedbackScreenshotHost {
                        DebugOverlay(ringLogBuffer = ringLogBuffer) {
                            AppNavHost(startDestination = resolvedStartDestination)
                        }
                    }
                }

                // 本模板骨架页面没有真正的异步加载,内容一画出来就算"完全显示"。
                // 真实项目里应该把这一行挪到实际数据加载完成的地方。
                LaunchedEffect(Unit) {
                    reportFullyDrawn()
                    memorySampler.sampleOnColdStartComplete()
                }
            }
        }
    }

    override fun onPause() {
        super.onPause()
        jankReporter.flush()
    }
}
