package io.sanato.apptemplate.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
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

/**
 * `UpdateCheckHost` 包住整个 NavHost:持有更新检查跨屏幕状态、展示更新对话框、
 * UpToDate/Error 时弹 Toast——见 `:feature-update` README。`onCheckForUpdates`
 * 回调线穿进 `settingsGraph`,`:feature-settings` 不需要知道"更新检查"这件事
 * 存在;`onNavigateToFeedback`/`onNavigateToLicenses` 同理让
 * `:feature-feedback`/`:feature-licenses` 不需要互相知道对方存在。
 */
@Composable
fun AppNavHost(
    startDestination: Any,
    navController: NavHostController = rememberNavController(),
) {
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
