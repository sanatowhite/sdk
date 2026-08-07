package io.sanato.apptemplate.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.navigation.NavController
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import io.sanato.appkit.core.telemetry.Telemetry
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
 */
@Composable
fun AppNavHost(
    startDestination: Any,
    telemetry: Telemetry,
    navController: NavHostController = rememberNavController(),
    onScreenChanged: (String) -> Unit = {},
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

    UpdateCheckHost { onCheckForUpdates ->
        NavHost(navController = navController, startDestination = startDestination) {
            composable<Home> {
                HomeScreen(onNavigateToSettings = { navController.navigate(SettingsRoute) })
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
            )
            feedbackGraph(navController)
            licensesGraph(navController, librariesRawRes = R.raw.aboutlibraries)
        }
    }
}
