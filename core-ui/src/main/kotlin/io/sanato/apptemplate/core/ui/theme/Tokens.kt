package io.sanato.apptemplate.core.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 颜色的唯一真源是 M3 的 `ColorScheme`(见 [AppTemplateTheme])——这里只补
 * `ColorScheme` 不覆盖的部分:间距。动效直接用 `MaterialTheme.motionScheme`
 * (M3 1.4.0 引入),不在这里重新发明一套动效 token。
 */
data class Spacing(
    val xs: Dp = 4.dp,
    val sm: Dp = 8.dp,
    val md: Dp = 16.dp,
    val lg: Dp = 24.dp,
    val xl: Dp = 32.dp,
)

internal val LocalSpacing = staticCompositionLocalOf { Spacing() }

/**
 * 用法:`AppTheme.spacing.md`——与 `MaterialTheme.colorScheme`/`MaterialTheme.typography`
 * 保持同一种调用手感,不引入第二套访问方式。
 */
object AppTheme {
    val spacing: Spacing
        @Composable
        get() = LocalSpacing.current
}
