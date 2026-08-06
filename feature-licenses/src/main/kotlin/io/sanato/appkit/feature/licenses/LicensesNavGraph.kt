package io.sanato.appkit.feature.licenses

import androidx.annotation.RawRes
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable

/**
 * 挂进消费方自己的 `NavHost` 里:
 *
 * ```
 * NavHost(navController, startDestination = Home) {
 *     composable<Home> { ... }
 *     licensesGraph(navController, librariesRawRes = R.raw.aboutlibraries)
 * }
 * ```
 *
 * `librariesRawRes` 要求消费方自己 apply `com.mikepenz.aboutlibraries.plugin`
 * 生成——见 [LicensesScreen] 的说明。
 */
fun NavGraphBuilder.licensesGraph(
    navController: NavController,
    @RawRes librariesRawRes: Int,
) {
    composable<LicensesRoute> {
        LicensesScreen(
            librariesRawRes = librariesRawRes,
            onNavigateBack = { navController.popBackStack() },
        )
    }
}
