package io.sanato.apptemplate.debug

import androidx.compose.runtime.Composable
import io.sanato.appkit.core.telemetry.RingLogBuffer

/**
 * staging 门面:和 release 一样内联直通——`staging` buildType 是
 * `initWith(getByName("release"))`(不可调试,发给内部测试用),同样不应该带
 * Debug Drawer。三个 buildType(debug/release/staging)各自的 source set
 * 都必须提供这个同名函数,AGP 不会自动帮缺失的 buildType 补一份。
 */
@Composable
inline fun DebugOverlay(
    ringLogBuffer: RingLogBuffer,
    content: @Composable () -> Unit,
) {
    content()
}
