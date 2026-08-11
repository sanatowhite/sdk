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
import androidx.compose.ui.res.stringResource
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.AndroidEntryPoint
import io.sanato.appkit.core.auth.AuthRepository
import io.sanato.appkit.core.auth.AuthState
import io.sanato.appkit.core.telemetry.RingLogBuffer
import io.sanato.appkit.core.telemetry.Telemetry
import io.sanato.appkit.core.telemetry.jank.ScreenJankReporter
import io.sanato.appkit.core.telemetry.memory.MemorySampler
import io.sanato.appkit.core.ui.theme.AppTemplateTheme
import io.sanato.appkit.feature.feedback.FeedbackScreenshotHost
import io.sanato.appkit.feature.settings.AppEntryViewModel
import io.sanato.appkit.feature.settings.ConsentRoute
import io.sanato.apptemplate.R
import io.sanato.apptemplate.debug.DebugOverlay
import io.sanato.apptemplate.navigation.AppNavHost
import io.sanato.apptemplate.navigation.Home
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

    // 只用来给 Home/:feature-settings 的"账号"入口派生 accountSubtitle(当前
    // 登录邮箱/手机号)——:feature-settings 本身不知道 AuthState 存在,这份翻译
    // 只能住在 :app 里。
    @Inject
    lateinit var authRepository: AuthRepository

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
        //
        // ⚠️ 刻意不在这里接 `:feature-auth` 的 `AuthEntryViewModel.signInRequired`
        // 做登录门禁——这个模板没有"必须登录才能用"的产品理由,登录只是 Home 页上
        // 众多可体验能力之一(见 HomeScreen 的"账号"条目和 AppNavHost 里
        // onNavigateToAccount 的分支逻辑)。真要做"同意 → 登录 → 首页"强制门禁的
        // fork,把 `AuthEntryViewModel.signInRequired` 接回这里、按
        // `feature-auth/README.md` 里那条(现已标注为"可选")的三态合成写法接线
        // 即可——`:feature-auth` 的公开 API 本身没有变化。
        splashScreen.setKeepOnScreenCondition {
            entryViewModel.consentRequired.value == null
        }

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
            val consentRequired by entryViewModel.consentRequired.collectAsStateWithLifecycle()
            val authState by authRepository.authState.collectAsStateWithLifecycle()
            val isSignedIn = authState is AuthState.SignedIn
            // 未登录时给一个"点击登录"的提示,而不是留空字幕——账号入口在这个模板里
            // 不是登录门禁,是 Home/Settings 上的一个可选功能,不提示的话不明显能
            // 点进去就是登录页。两处入口(Home + Settings)共用同一份文案,所以在
            // 这里算一次,而不是让 HomeScreen/SettingsScreen 各自兜底一遍。
            val accountSubtitle =
                (authState as? AuthState.SignedIn)?.user?.let { it.email ?: it.phoneNumber }
                    ?: stringResource(R.string.home_account_signed_out_hint)
            val resolvedStartDestination = consentRequired?.let { if (it) ConsentRoute else Home }
            if (resolvedStartDestination != null) {
                AppTemplateTheme(darkTheme = darkTheme) {
                    // FeedbackScreenshotHost 包住内容根节点,给 :feature-feedback 的
                    // "附带截图"功能提供捕获点——不用自己摆弄 GraphicsLayer。
                    FeedbackScreenshotHost {
                        DebugOverlay(ringLogBuffer = ringLogBuffer) {
                            AppNavHost(
                                startDestination = resolvedStartDestination,
                                isSignedIn = isSignedIn,
                                accountSubtitle = accountSubtitle,
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
