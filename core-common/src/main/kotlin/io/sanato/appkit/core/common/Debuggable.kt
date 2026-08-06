package io.sanato.appkit.core.common

import android.content.Context
import android.content.pm.ApplicationInfo

/**
 * 库模块拿不到消费方的 `BuildConfig.DEBUG`——那是编译期生成在消费方自己包名下
 * 的类。用 `ApplicationInfo.FLAG_DEBUGGABLE` 判断语义上更准确："要不要打印
 * 调试日志"本来就该跟着 `isDebuggable`（清单/签名决定）走，而不是跟着
 * buildType 名字走；三个默认 buildType（debug/release/staging）下二者行为
 * 完全一致。
 */
fun Context.isDebuggableBuild(): Boolean = (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
