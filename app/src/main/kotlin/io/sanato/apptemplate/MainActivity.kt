package io.sanato.apptemplate

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import dagger.hilt.android.AndroidEntryPoint
import io.sanato.apptemplate.core.telemetry.Telemetry
import io.sanato.apptemplate.core.telemetry.jank.ScreenJankReporter
import io.sanato.apptemplate.core.telemetry.memory.MemorySampler
import io.sanato.apptemplate.core.ui.theme.AppTemplateTheme
import io.sanato.apptemplate.navigation.AppNavHost
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

    private lateinit var jankReporter: ScreenJankReporter

    override fun onCreate(savedInstanceState: Bundle?) {
        // 必须在 super.onCreate() 之前调用,core-splashscreen 才能接管
        // Theme.AppTemplate.Starting -> Theme.AppTemplate 的主题切换时机。
        installSplashScreen()
        super.onCreate(savedInstanceState)

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
            AppTemplateTheme(darkTheme = darkTheme) {
                AppNavHost()
            }

            // 本模板骨架页面没有真正的异步加载,内容一画出来就算"完全显示"。
            // 真实项目里应该把这一行挪到实际数据加载完成的地方。
            LaunchedEffect(Unit) {
                reportFullyDrawn()
                memorySampler.sampleOnColdStartComplete()
            }
        }
    }

    override fun onPause() {
        super.onPause()
        jankReporter.flush()
    }
}
