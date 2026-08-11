package io.sanato.appkit.smoke

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import dagger.hilt.android.AndroidEntryPoint
import io.sanato.appkit.core.ui.theme.AppTemplateTheme
import io.sanato.appkit.feature.feedback.FeedbackRoute
import io.sanato.appkit.feature.feedback.FeedbackScreenshotHost
import io.sanato.appkit.feature.feedback.feedbackGraph
import io.sanato.appkit.feature.licenses.LicensesRoute
import io.sanato.appkit.feature.licenses.licensesGraph
import io.sanato.appkit.feature.settings.settingsGraph
import io.sanato.appkit.feature.update.UpdateCheckHost
import kotlinx.serialization.Serializable
import javax.inject.Inject

@Serializable
private data object SmokeHome

/**
 * 把 `:feature-settings`/`:feature-feedback`/`:feature-licenses`/
 * `:feature-update` 全部接在同一个 `NavHost` 里——不只是"每个函数单独能编译",
 * 而是它们互相之间的可空回调解耦(`onNavigateToFeedback`/`onNavigateToLicenses`/
 * `onCheckForUpdates`)在真实坐标消费下也能正确接线。`librariesRawRes` 用 `0`
 * 占位——这里只验证编译期的类型/坐标解析,不追求运行期渲染正确(AboutLibraries
 * 生成的真实资源需要消费方自己 apply 插件,不是这个 smoke 工程的验证目标)。
 *
 * `netSmoke`/`dataSmoke`/`dispatcherSmoke`/`backupSmoke`/`authSmoke`/`downloadSmoke`
 * 六个 `@Inject` 字段是刻意加的:光是 `@Inject constructor` 存在、没有任何入口点
 * 引用,Dagger 不会去校验它们的 provision graph(可达性剪枝),只有 Kotlin 编译器
 * 会检查类型解析。挂在 `@AndroidEntryPoint` 的字段上,才会真正逼 `hiltJavaCompile`
 * 走一遍完整绑定解析——这正是本模块要验证的"发布出去的 AAR 上 Hilt 聚合是否依然
 * 成立"。
 * `authSmoke` 额外验证 `:auth-firebase` 的 `FirebaseAuthModule` 绑定
 * (`AuthRepository`/`AuthTokenProvider`)和 `:auth-net-hilt` 的
 * `@Authenticated OkHttpClient` 绑定在没有真实 `google-services.json` 的
 * consumer 里也能完整解析——`FirebaseAuth.getInstance()` 是 lazy,不在
 * `@Provides` 构造期调用,所以字段注入本身不会在 `onCreate` 崩溃(见
 * `AuthSmoke`/`FirebaseAuthRepository` 各自的 KDoc)。
 * `downloadSmoke` 验证 `:downloadkit-hilt` 的 `DownloadModule` 绑定
 * (`Downloader`)在没有任何 `NetworkMetricsSink`/`DownloadConfigOverride`/
 * `DownloadNotifier` 覆盖绑定的 consumer 里也能完整解析——三个
 * `@BindsOptionalOf` 钩子全部落空时(`Optional.empty()`)`DownloadModule`
 * 仍必须能编译出一个可用的 `Downloader`。
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject
    lateinit var netSmoke: NetSmoke

    @Inject
    lateinit var dataSmoke: DataSmoke

    @Inject
    lateinit var dispatcherSmoke: DispatcherSmoke

    @Inject
    lateinit var backupSmoke: BackupSmoke

    @Inject
    lateinit var authSmoke: AuthSmoke

    @Inject
    lateinit var downloadSmoke: DownloadSmoke

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AppTemplateTheme {
                FeedbackScreenshotHost {
                    UpdateCheckHost { onCheckForUpdates ->
                        val navController = rememberNavController()
                        NavHost(navController = navController, startDestination = SmokeHome) {
                            composable<SmokeHome> { SmokeHomeScreen() }
                            settingsGraph(
                                navController = navController,
                                onNavigateToFeedback = { navController.navigate(FeedbackRoute) },
                                onNavigateToLicenses = { navController.navigate(LicensesRoute) },
                                onCheckForUpdates = onCheckForUpdates,
                            )
                            feedbackGraph(navController)
                            licensesGraph(navController, librariesRawRes = 0)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SmokeHomeScreen() {
    Text("consumer-smoke")
}
