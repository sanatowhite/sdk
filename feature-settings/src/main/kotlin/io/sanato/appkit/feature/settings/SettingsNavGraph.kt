package io.sanato.appkit.feature.settings

import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable

/**
 * 挂进消费方自己的 `NavHost` 里:
 *
 * ```
 * NavHost(navController, startDestination = Home) {
 *     composable<Home> { ... }
 *     settingsGraph(
 *         navController = navController,
 *         content = StandardPagesContent(
 *             privacyPolicyRawRes = R.raw.privacy_policy,
 *             termsOfServiceRawRes = R.raw.terms_of_service,
 *             changelogRawRes = R.raw.changelog,
 *         ),
 *         onNavigateToFeedback = { navController.navigate(FeedbackRoute) }, // 引了 :feature-feedback 才传
 *         onNavigateToLicenses = { navController.navigate(LicensesRoute) }, // 引了 :feature-licenses 才传
 *     )
 * }
 * ```
 *
 * 可空回调让这个模块和 `:feature-feedback`/`:feature-licenses`/`:feature-update`
 * 之间零依赖——不传就是那一行不显示,不是抛异常。
 *
 * `onConsentAccepted` 同理是可空的:同意后"该去哪个首页"是消费方自己的路由图
 * 决定的,这个模块不知道也不应该知道那个路由长什么样。不传就退回
 * `popBackStack()`(适合"从设置页里再次查看同意页"这种非首启场景);首启同意
 * 流程(配合 [AppEntryViewModel])必须传,导航到消费方自己的首页路由。
 */
fun NavGraphBuilder.settingsGraph(
    navController: NavController,
    content: StandardPagesContent = StandardPagesContent(),
    config: SettingsPageConfig = SettingsPageConfig(),
    onNavigateToFeedback: (() -> Unit)? = null,
    onNavigateToLicenses: (() -> Unit)? = null,
    onCheckForUpdates: (() -> Unit)? = null,
    onConsentAccepted: (() -> Unit)? = null,
) {
    composable<SettingsRoute> {
        SettingsRoute(
            config = config,
            content = content,
            onNavigateBack = { navController.popBackStack() },
            onNavigateToAbout = { navController.navigate(AboutRoute) },
            onNavigateToPrivacyPolicy = { navController.navigate(PrivacyPolicyRoute) },
            onNavigateToTermsOfService = { navController.navigate(TermsOfServiceRoute) },
            onNavigateToFeedback = onNavigateToFeedback,
            onCheckForUpdates = onCheckForUpdates,
        )
    }
    composable<AboutRoute> {
        AboutRoute(
            content = content,
            onNavigateBack = { navController.popBackStack() },
            onNavigateToLicenses = onNavigateToLicenses,
        )
    }
    if (content.privacyPolicyRawRes != null) {
        composable<PrivacyPolicyRoute> {
            val context = LocalContext.current
            val title = stringResource(R.string.appkit_settings_privacy_policy)
            val markdown =
                remember {
                    context.resources
                        .openRawResource(
                            content.privacyPolicyRawRes,
                        ).bufferedReader()
                        .use { it.readText() }
                }
            LegalDocScreen(title = title, markdown = markdown, onNavigateBack = { navController.popBackStack() })
        }
    }
    if (content.termsOfServiceRawRes != null) {
        composable<TermsOfServiceRoute> {
            val context = LocalContext.current
            val title = stringResource(R.string.appkit_settings_terms_of_service)
            val markdown =
                remember {
                    context.resources
                        .openRawResource(
                            content.termsOfServiceRawRes,
                        ).bufferedReader()
                        .use { it.readText() }
                }
            LegalDocScreen(title = title, markdown = markdown, onNavigateBack = { navController.popBackStack() })
        }
    }
    composable<ConsentRoute> {
        ConsentRoute(
            onAccepted =
                onConsentAccepted ?: {
                    navController.popBackStack()
                    Unit
                },
            onViewPrivacyPolicy = { navController.navigate(PrivacyPolicyRoute) },
            onViewTermsOfService = { navController.navigate(TermsOfServiceRoute) },
        )
    }
}
