package io.sanato.apptemplate.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import io.sanato.apptemplate.R
import io.sanato.apptemplate.about.AboutScreen
import io.sanato.apptemplate.about.LicensesScreen
import io.sanato.apptemplate.consent.ConsentScreen
import io.sanato.apptemplate.legal.LegalDocScreen
import io.sanato.apptemplate.settings.SettingsScreen
import io.sanato.apptemplate.ui.home.HomeScreen

@Composable
fun AppNavHost(
    startDestination: Any,
    navController: NavHostController = rememberNavController(),
) {
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
            )
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
