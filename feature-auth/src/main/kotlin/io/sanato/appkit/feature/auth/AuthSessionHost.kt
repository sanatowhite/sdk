package io.sanato.appkit.feature.auth

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import io.sanato.appkit.core.auth.AuthRepository
import io.sanato.appkit.core.auth.AuthState
import io.sanato.appkit.core.auth.SignOutReason
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.runningFold
import javax.inject.Inject

/**
 * Only fires on a genuine `SignedIn → SignedOut` *transition*.
 *
 * ⚠️ Cold start for a never-signed-in user produces `Unknown → SignedOut`,
 * which is not a transition worth reacting to — treating it as one would
 * fire a redundant navigation the instant a fresh `AuthGraphRoute` start
 * destination is already showing, plus a spurious `screenView` report.
 * `runningFold` is what lets this distinguish "just arrived at SignedOut"
 * from "was already SignedOut".
 */
@HiltViewModel
class AuthSessionViewModel
    @Inject
    constructor(
        authRepository: AuthRepository,
    ) : ViewModel() {
        val signedOutEvents: Flow<SignOutReason> =
            authRepository.authState
                .runningFold<AuthState, Pair<AuthState?, AuthState?>>(null to null) { acc, next -> acc.second to next }
                .mapNotNull { (previous, current) ->
                    if (previous is AuthState.SignedIn && current is AuthState.SignedOut) current.reason else null
                }
    }

/**
 * Wraps your own `NavHost`, the same shape as `:feature-update`'s
 * `UpdateCheckHost`:
 *
 * ```kotlin
 * AuthSessionHost(
 *     onSignedOut = { reason ->
 *         navController.navigate(AuthGraphRoute) {
 *             popUpTo(navController.graph.findStartDestination().id) { inclusive = true }
 *             launchSingleTop = true
 *         }
 *     },
 * ) {
 *     NavHost(navController, startDestination) { ... }
 * }
 * ```
 *
 * This is the **only** place that decides "where to go after sign-out" — a
 * user tapping "sign out" on [AccountScreen] and the backend force-expiring a
 * session (deleted/disabled account, revoked refresh token) both end up as
 * the exact same `SignedIn → SignedOut` transition here, with only the
 * [SignOutReason] differing. Splitting this into a per-screen callback *and*
 * a separate global listener would double-navigate the moment a user taps
 * "sign out" while both paths are wired up.
 */
@Composable
fun AuthSessionHost(
    onSignedOut: (reason: SignOutReason) -> Unit,
    viewModel: AuthSessionViewModel = hiltViewModel(),
    content: @Composable () -> Unit,
) {
    LaunchedEffect(viewModel) {
        viewModel.signedOutEvents.collect { reason -> onSignedOut(reason) }
    }
    content()
}
