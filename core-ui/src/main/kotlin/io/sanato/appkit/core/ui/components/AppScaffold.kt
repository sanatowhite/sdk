package io.sanato.appkit.core.ui.components

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * 模板里所有页面共用的壳层。`contentWindowInsets = safeDrawing` 是 edge-to-edge 下
 * 页面内容不被系统栏遮挡的关键一行——targetSdk 36+ 起无法退出 edge-to-edge,
 * 每个页面都必须处理 insets,而不是只在 Activity 层处理一次就够。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppScaffold(
    modifier: Modifier = Modifier,
    topBar: @Composable () -> Unit = {},
    snackbarHostState: SnackbarHostState? = null,
    content: @Composable (PaddingValues) -> Unit,
) {
    Scaffold(
        modifier = modifier,
        topBar = topBar,
        contentWindowInsets = WindowInsets.safeDrawing,
        snackbarHost = {
            snackbarHostState?.let { androidx.compose.material3.SnackbarHost(it) }
        },
        content = content,
    )
}
