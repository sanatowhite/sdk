package io.sanato.appkit.feature.auth

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation

/**
 * 挂进消费方自己的 `NavHost` 里:
 *
 * ```kotlin
 * NavHost(navController, startDestination = startDestination) {
 *     composable<Home> { ... }
 *     authGraph(
 *         navController = navController,
 *         onSignedIn = { navController.navigate(Home) { popUpTo(AuthGraphRoute) { inclusive = true } } },
 *     )
 * }
 * ```
 *
 * 和 `settingsGraph` 同一条规则:跨 feature 的依赖全走可空回调,不传 = 那一行
 * 不显示,模块间零 import。
 *
 * ⚠️ 刻意【没有】`onSignedOut` 回调——登出后的导航由 [AuthSessionHost] 统一
 * 负责,见该函数的 KDoc:"用户主动登出"和"token 被服务端作废"必须走同一条
 * 清栈路径,拆成两个 navigator 会产生竞态和重复导航。
 */
fun NavGraphBuilder.authGraph(
    navController: NavController,
    config: AuthPageConfig = AuthPageConfig(),
    onSignedIn: (() -> Unit)? = null,
) {
    navigation<AuthGraphRoute>(startDestination = SignInRoute) {
        composable<SignInRoute> {
            SignInRoute(
                onSignedIn =
                    onSignedIn ?: {
                        navController.popBackStack()
                        Unit
                    },
                onNavigateToSignUp = { navController.navigate(SignUpRoute) },
                onNavigateToForgotPassword = { navController.navigate(ForgotPasswordRoute) },
                onNavigateToPhone = { navController.navigate(PhoneNumberRoute) },
                config = config,
            )
        }
        composable<SignUpRoute> {
            SignUpRoute(
                onSignedUp =
                    onSignedIn ?: {
                        navController.popBackStack()
                        Unit
                    },
                onNavigateBack = { navController.popBackStack() },
            )
        }
        composable<ForgotPasswordRoute> {
            ForgotPasswordRoute(onNavigateBack = { navController.popBackStack() })
        }
        composable<PhoneNumberRoute> {
            PhoneNumberRoute(
                onCodeSent = { verificationId, masked ->
                    navController.navigate(PhoneCodeRoute(verificationId, masked))
                },
                onSignedIn =
                    onSignedIn ?: {
                        navController.popBackStack()
                        Unit
                    },
                onNavigateBack = { navController.popBackStack() },
            )
        }
        composable<PhoneCodeRoute> {
            PhoneCodeRoute(
                onVerified =
                    onSignedIn ?: {
                        navController.popBackStack()
                        Unit
                    },
                onNavigateBack = { navController.popBackStack() },
            )
        }
    }
    // 已登录态,刻意放在嵌套图【外面】——它不该被"登录成功后清空 AuthGraphRoute"波及。
    composable<AccountRoute> {
        AccountRoute(onNavigateBack = { navController.popBackStack() })
    }
}
