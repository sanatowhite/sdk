package io.sanato.apptemplate.debug

import androidx.compose.runtime.Composable
import io.sanato.apptemplate.core.telemetry.RingLogBuffer

/**
 * release 门面:内联直通,不引用 `:debug-tools` 的任何类型——那个模块本来就
 * 不在 release 的编译/运行时 classpath 上(`debugImplementation`),这里
 * 只是让 `:app` 的其余(buildType 无关的)代码能无条件调用同一个函数名。
 */
@Composable
inline fun DebugOverlay(
    ringLogBuffer: RingLogBuffer,
    content: @Composable () -> Unit,
) {
    content()
}
