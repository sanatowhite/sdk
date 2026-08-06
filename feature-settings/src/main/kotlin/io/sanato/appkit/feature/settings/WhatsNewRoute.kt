package io.sanato.appkit.feature.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * 挂在宿主的 Home/入口页里调用——`content.changelogRawRes` 为 `null` 时整个
 * 弹窗逻辑不触发。展示完(或判定不需要展示)后自动调用一次 `markSeen()`。
 */
@Composable
fun WhatsNewRoute(
    content: StandardPagesContent = StandardPagesContent(),
    viewModel: WhatsNewViewModel = hiltViewModel(),
) {
    val changelogRawRes = content.changelogRawRes ?: return
    val context = LocalContext.current
    val shouldShow by viewModel.shouldShow.collectAsStateWithLifecycle()

    when (shouldShow) {
        true -> {
            val latestEntry =
                remember(changelogRawRes) {
                    ChangelogReader.read(context, changelogRawRes).maxByOrNull { it.versionCode }
                }
            WhatsNewSheet(latestEntry = latestEntry, onDismiss = viewModel::markSeen)
        }

        false -> {
            LaunchedEffect(Unit) { viewModel.markSeen() }
        }

        null -> {
            Unit
        } // 还不知道,等 DataStore 真实值到达。
    }
}
