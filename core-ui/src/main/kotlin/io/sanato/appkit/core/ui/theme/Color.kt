package io.sanato.appkit.core.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

// 品牌 seed color——API 24-30(无动态取色)以及关闭动态取色时的静态兜底方案。
// fork 出去的项目通常会替换成自己的品牌色,这里只需要一组自洽、可读的默认值。
private val SeedPrimary = Color(0xFF3B6939)
private val SeedSecondary = Color(0xFF53634F)
private val SeedTertiary = Color(0xFF38656B)

internal val LightColorScheme =
    lightColorScheme(
        primary = SeedPrimary,
        secondary = SeedSecondary,
        tertiary = SeedTertiary,
    )

internal val DarkColorScheme =
    darkColorScheme(
        primary = Color(0xFFA1D399),
        secondary = Color(0xFFBBCCB4),
        tertiary = Color(0xFFA1CED4),
    )
