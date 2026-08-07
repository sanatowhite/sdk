package io.sanato.appkit.feature.feedback

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable

/**
 * 挂进消费方自己的 `NavHost` 里:
 *
 * ```
 * NavHost(navController, startDestination = Home) {
 *     composable<Home> { ... }
 *     feedbackGraph(navController)
 * }
 * ```
 */
fun NavGraphBuilder.feedbackGraph(navController: NavController) {
    composable<FeedbackRoute> {
        FeedbackRoute(onNavigateBack = { navController.popBackStack() })
    }
}
