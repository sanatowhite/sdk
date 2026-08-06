package io.sanato.apptemplate

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.platform.LocalGraphicsContext
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.AndroidEntryPoint
import io.sanato.apptemplate.core.telemetry.RingLogBuffer
import io.sanato.apptemplate.core.telemetry.Telemetry
import io.sanato.apptemplate.core.telemetry.jank.ScreenJankReporter
import io.sanato.apptemplate.core.telemetry.memory.MemorySampler
import io.sanato.apptemplate.core.ui.theme.AppTemplateTheme
import io.sanato.apptemplate.debug.DebugOverlay
import io.sanato.apptemplate.feedback.AppScreenshot
import io.sanato.apptemplate.navigation.AppNavHost
import io.sanato.apptemplate.splash.AppEntryViewModel
import io.sanato.logkit.LogKit
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
        splashScreen.setKeepOnScreenCondition { entryViewModel.startDestination.value == null }

        // targetSdk 36+ 起无法退出 edge-to-edge,这里无条件调用一次而不是判断版本——
        // 同时覆盖了 24-35 仍需要显式开启的情况。
        enableEdgeToEdge()

        jankReporter = ScreenJankReporter(window, telemetry)
        // 不再硬编码 onScreenChanged("Home")——真实起始目的地可能是 Consent,
        // 且 `AppNavHost` 的 `OnDestinationChangedListener` 一注册就会用真实
        // 当前目的地调一次(NavController 的既有语义),这里手动预设只会在
        // 走 Consent 分支时把第一屏的卡顿错记到 "Home"。

        setContent {
            val darkTheme = isSystemInDarkTheme()
            SideEffect {
                WindowCompat.getInsetsController(window, window.decorView).apply {
                    isAppearanceLightStatusBars = !darkTheme
                    isAppearanceLightNavigationBars = !darkTheme
                }
            }
            val startDestination by entryViewModel.startDestination.collectAsStateWithLifecycle()
            val resolvedStartDestination = startDestination
            if (resolvedStartDestination != null) {
                AppTemplateTheme(darkTheme = darkTheme) {
                    // `rememberGraphicsLayer()` 这个便捷 Composable 在当前 Compose UI
                    // 版本里不存在——手动走 `LocalGraphicsContext` 创建/释放。
                    val graphicsContext = LocalGraphicsContext.current
                    val graphicsLayer = remember { graphicsContext.createGraphicsLayer() }
                    DisposableEffect(graphicsContext) {
                        onDispose { graphicsContext.releaseGraphicsLayer(graphicsLayer) }
                    }
                    SideEffect { AppScreenshot.register(graphicsLayer) }
                    Box(
                        modifier =
                            Modifier.drawWithContent {
                                drawContent()
                                graphicsLayer.record { this@drawWithContent.drawContent() }
                            },
                    ) {
                        DebugOverlay(ringLogBuffer = ringLogBuffer) {
                            AppNavHost(
                                startDestination = resolvedStartDestination,
                                telemetry = telemetry,
                                onScreenChanged = jankReporter::onScreenChanged,
                            )
                        }
                    }
                }

                // 本模板骨架页面没有真正的异步加载,内容一画出来就算"完全显示"。
                // 真实项目里应该把这一行挪到实际数据加载完成的地方。
                LaunchedEffect(Unit) {
                    reportFullyDrawn()
                    memorySampler.sampleOnColdStartComplete()
                    LogKit.i("App", "TTFD reported")
                }
            }
        }
    }

    override fun onPause() {
        super.onPause()
        jankReporter.flush()
    }
}
