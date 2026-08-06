package io.sanato.appkit.feature.feedback

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.platform.LocalGraphicsContext

/**
 * 包住宿主内容根节点,给反馈页的"附带截图"功能提供捕获点——一行接入,消费方
 * 不需要手动摆弄 `LocalGraphicsContext`/`GraphicsLayer` 的创建与释放。
 *
 * ```kotlin
 * FeedbackScreenshotHost {
 *     AppNavHost(startDestination = Home)
 * }
 * ```
 */
@Composable
fun FeedbackScreenshotHost(content: @Composable () -> Unit) {
    val graphicsContext = LocalGraphicsContext.current
    val graphicsLayer = remember { graphicsContext.createGraphicsLayer() }
    DisposableEffect(graphicsContext) {
        onDispose { graphicsContext.releaseGraphicsLayer(graphicsLayer) }
    }
    SideEffect { FeedbackScreenshot.register(graphicsLayer) }

    Box(
        modifier =
            Modifier.drawWithContent {
                drawContent()
                graphicsLayer.record { this@drawWithContent.drawContent() }
            },
    ) {
        content()
    }
}
