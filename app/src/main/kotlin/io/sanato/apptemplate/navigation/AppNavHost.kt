package io.sanato.apptemplate.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.navigation.NavController
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import io.sanato.apptemplate.R
import io.sanato.apptemplate.about.AboutScreen
import io.sanato.apptemplate.about.LicensesScreen
import io.sanato.apptemplate.consent.ConsentScreen
import io.sanato.apptemplate.core.telemetry.Telemetry
import io.sanato.apptemplate.feedback.FeedbackScreen
import io.sanato.apptemplate.legal.LegalDocScreen
import io.sanato.apptemplate.settings.SettingsScreen
import io.sanato.apptemplate.ui.home.HomeScreen
import io.sanato.logkit.LogKit

/**
 * [telemetry] 只用来做一件事:每次目的地变化时调 [Telemetry.screenView]。
 * 这修的是一个真实的既有漏洞——`MainActivity` 里 `jankReporter.onScreenChanged("Home")`
 * 硬编码且从不更新,导致目前所有卡顿都被错误归到 "Home"。一处 `DisposableEffect`
 * 覆盖全部 8 条路由,比给每个 Screen 都加一个 `LaunchedEffect` 更不容易漏。
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

    NavHost(navController = navController, startDestination = startDestination) {
        composable<Home> {
            HomeScreen(onNavigateToSettings = { navController.navigate(Settings) })
        }
        composable<Settings> {
            SettingsScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToAbout = { navController.navigate(About) },
                onNavigateToPrivacyPolicy = { navController.navigate(PrivacyPolicy) },
                onNavigateToTermsOfService = { navController.navigate(TermsOfService) },
                onNavigateToFeedback = { navController.navigate(Feedback) },
            )
        }
        composable<Feedback> {
            FeedbackScreen(onNavigateBack = { navController.popBackStack() })
        }
        composable<About> {
            AboutScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToLicenses = { navController.navigate(Licenses) },
            )
        }
        composable<Licenses> {
            LicensesScreen(onNavigateBack = { navController.popBackStack() })
        }
        composable<PrivacyPolicy> {
            LegalDocScreen(
                title = navController.context.getString(R.string.settings_privacy_policy),
                rawResId = R.raw.privacy_policy,
                onNavigateBack = { navController.popBackStack() },
            )
        }
        composable<TermsOfService> {
            LegalDocScreen(
                title = navController.context.getString(R.string.settings_terms_of_service),
                rawResId = R.raw.terms_of_service,
                onNavigateBack = { navController.popBackStack() },
            )
        }
        composable<Consent> {
            ConsentScreen(
                onAccepted = {
                    navController.navigate(Home) {
                        popUpTo(Consent) { inclusive = true }
                    }
                },
                onViewPrivacyPolicy = { navController.navigate(PrivacyPolicy) },
                onViewTermsOfService = { navController.navigate(TermsOfService) },
            )
        }
    }
}
