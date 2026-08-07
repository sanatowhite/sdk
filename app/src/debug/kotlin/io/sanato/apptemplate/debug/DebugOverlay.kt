package io.sanato.apptemplate.debug

import androidx.compose.runtime.Composable
import io.sanato.appkit.core.telemetry.RingLogBuffer
import io.sanato.appkit.debugtools.DebugDrawer

/**
 * debug 门面:真的包一层 Debug Drawer。`:app` 唯一依赖 `:debug-tools` 的入口——
 * MainActivity 调用这个同名函数,不需要关心自己是 debug 还是 release 构建,
 * `release` 门面(见 `app/src/release/...`)提供一个内联透传的同名函数。
 */
@Composable
fun DebugOverlay(
    ringLogBuffer: RingLogBuffer,
    content: @Composable () -> Unit,
) {
    DebugDrawer(
        ringLogBuffer = ringLogBuffer,
        extraContent = { LogKitDebugPanel() },
        content = content,
    )
}
