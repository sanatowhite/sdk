package io.sanato.apptemplate.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.navigation.NavController
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import io.sanato.appkit.core.telemetry.Telemetry
import io.sanato.appkit.feature.auth.AccountRoute
import io.sanato.appkit.feature.auth.AuthGraphRoute
import io.sanato.appkit.feature.auth.AuthSessionHost
import io.sanato.appkit.feature.auth.authGraph
import io.sanato.appkit.feature.feedback.FeedbackRoute
import io.sanato.appkit.feature.feedback.feedbackGraph
import io.sanato.appkit.feature.licenses.LicensesRoute
import io.sanato.appkit.feature.licenses.licensesGraph
import io.sanato.appkit.feature.settings.ConsentRoute
import io.sanato.appkit.feature.settings.SettingsRoute
import io.sanato.appkit.feature.settings.StandardPagesContent
import io.sanato.appkit.feature.settings.settingsGraph
import io.sanato.appkit.feature.update.UpdateCheckHost
import io.sanato.apptemplate.R
import io.sanato.apptemplate.ui.home.HomeScreen
import io.sanato.apptemplate.ui.websocket.WebSocketDemoRoute
import io.sanato.logkit.LogKit

/**
 * `UpdateCheckHost` 包住整个 NavHost:持有更新检查跨屏幕状态、展示更新对话框、
 * UpToDate/Error 时弹 Toast——见 `:feature-update` README。`onCheckForUpdates`
 * 回调线穿进 `settingsGraph`,`:feature-settings` 不需要知道"更新检查"这件事
 * 存在;`onNavigateToFeedback`/`onNavigateToLicenses` 同理让
 * `:feature-feedback`/`:feature-licenses` 不需要互相知道对方存在。
 *
 * [telemetry] 只用来做一件事:每次目的地变化时调 [Telemetry.screenView]。这修的
 * 是一个真实的既有漏洞——`MainActivity` 里 `jankReporter.onScreenChanged("Home")`
 * 硬编码且从不更新,导致目前所有卡顿都被错误归到 "Home"。一处 `DisposableEffect`
 * 覆盖全部路由,比给每个 Screen 都加一个 `LaunchedEffect` 更不容易漏。
 *
 * 登录在这个模板里刻意【不】是启动门禁——`MainActivity` 只用 `consentRequired`
 * 算 [startDestination],不掺 `:feature-auth` 的 `AuthEntryViewModel.signInRequired`
 * (那个信号仍然存在,是留给真的需要强制登录的 fork 用的,见其 KDoc)。登录只是
 * Home/Settings 页上"账号"这一个入口,未登录时点它进 [AuthGraphRoute],登录成功
 * 弹回来;[isSignedIn] 就是这两处 `onNavigateToAccount` 分支用的信号。
 *
 * 整个 [NavHost] 包在 [AuthSessionHost] 里:`SignedIn → SignedOut` 的每一次跳变
 * (用户主动登出,或者 token 被服务端作废)都从这里统一清栈导航回 [Home]——不
 * 在账号页单独接一个"登出"回调,两条路径会在用户点登出时产生双重导航竞态,见
 * `:feature-auth` README。`popUpTo(findStartDestination())` 而不是
 * `popUpTo<Home>`,是因为 `Home` 之下可能还有 `ConsentRoute` 没弹出去。
 *
 * [accountSubtitle] 是当前登录邮箱/手机号的展示串,由 `MainActivity` 从
 * `AuthState` 派生后原样转给 Home/`settingsGraph` 的"账号"入口——`:feature-settings`
 * 不知道 `AuthState` 长什么样,这份翻译只能住在 `:app` 里。
 */
@Composable
fun AppNavHost(
    startDestination: Any,
    telemetry: Telemetry,
    navController: NavHostController = rememberNavController(),
    onScreenChanged: (String) -> Unit = {},
    isSignedIn: Boolean = false,
    accountSubtitle: String? = null,
) {
    DisposableEffect(navController) {
        val listener =
            NavController.OnDestinationChangedListener { _, destination, _ ->
                val route = destination.route ?: "unknown"
                LogKit.i("Nav", "nav → $route")
                telemetry.screenView(route)
                // 修一个真实漏洞:调用方(MainActivity)的 ScreenJankReporter 之前
                // 硬编码 onScreenChanged("Home") 且从不更新,导致所有卡顿都被
                // 错误归到 "Home"。现在跟着真实路由走。
                onScreenChanged(route)
            }
        navController.addOnDestinationChangedListener(listener)
        onDispose { navController.removeOnDestinationChangedListener(listener) }
    }

    // 未登录时点"账号"去登录,登录后点"账号"去账号页——两处入口(Home + Settings)
    // 共享同一条分支逻辑,不重复写。
    val onNavigateToAccount: () -> Unit = {
        if (isSignedIn) navController.navigate(AccountRoute) else navController.navigate(AuthGraphRoute)
    }

    AuthSessionHost(
        onSignedOut = {
            navController.navigate(Home) {
                popUpTo(navController.graph.findStartDestination().id) { inclusive = true }
                launchSingleTop = true
            }
        },
    ) {
        UpdateCheckHost { onCheckForUpdates ->
            NavHost(navController = navController, startDestination = startDestination) {
                composable<Home> {
                    HomeScreen(
                        accountSubtitle = accountSubtitle,
                        onNavigateToSettings = { navController.navigate(SettingsRoute) },
                        onNavigateToFeedback = { navController.navigate(FeedbackRoute) },
                        onNavigateToLicenses = { navController.navigate(LicensesRoute) },
                        onCheckForUpdates = onCheckForUpdates,
                        onNavigateToAccount = onNavigateToAccount,
                        onNavigateToWebSocketDemo = { navController.navigate(WebSocketDemoRoute) },
                    )
                }
                composable<WebSocketDemoRoute> {
                    WebSocketDemoRoute(onNavigateBack = { navController.popBackStack() })
                }
                settingsGraph(
                    navController = navController,
                    content =
                        StandardPagesContent(
                            privacyPolicyRawRes = R.raw.privacy_policy,
                            termsOfServiceRawRes = R.raw.terms_of_service,
                            changelogRawRes = R.raw.changelog,
                        ),
                    onNavigateToFeedback = { navController.navigate(FeedbackRoute) },
                    onNavigateToLicenses = { navController.navigate(LicensesRoute) },
                    onCheckForUpdates = onCheckForUpdates,
                    onConsentAccepted = {
                        navController.navigate(Home) {
                            popUpTo(ConsentRoute) { inclusive = true }
                        }
                    },
                    onNavigateToAccount = onNavigateToAccount,
                    accountSubtitle = accountSubtitle,
                )
                authGraph(
                    navController = navController,
                    // AuthGraphRoute 现在是从 Home/Settings 的"账号"入口 push 上去的,
                    // 不再是 startDestination——登录成功只需要弹掉这个嵌套图自己的
                    // 所有目的地,回到下面本来就在的 Home/Settings,不需要再 navigate(Home)。
                    onSignedIn = { navController.popBackStack<AuthGraphRoute>(inclusive = true) },
                )
                feedbackGraph(navController)
                licensesGraph(navController, librariesRawRes = R.raw.aboutlibraries)
            }
        }
    }
}
