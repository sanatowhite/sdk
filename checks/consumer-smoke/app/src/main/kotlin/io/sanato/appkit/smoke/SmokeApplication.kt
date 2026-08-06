package io.sanato.appkit.smoke

import dagger.hilt.android.HiltAndroidApp
import io.sanato.appkit.core.telemetry.hilt.TelemetryApplication

/**
 * 证明 core-common-hilt/core-init-hilt/core-data-hilt/core-telemetry-hilt/
 * net-telemetry-hilt 五个 `-hilt` 伴生模块 + telemetry-firebase +
 * feature-settings/feature-feedback/feature-update 的 Hilt `@Module` 聚合,
 * 在【真实发布出去的 AAR】上(通过 mavenLocal()/JitPack 坐标解析,不是同一份
 * 源码树里的 project() 依赖)也能正确工作。
 *
 * 这不是重复劳动:Phase 0 的 spike 已经证明了"库模块的 @Module 必须自己跑
 * hilt-compiler 才会被聚合",但那次验证走的是 project() 依赖——Gradle 的
 * project substitution 机制本来就不会掩盖这类问题,project() 本来就测得出来。
 * 这里换成真正的 Maven 坐标消费,验证的是另一件事:发布流程本身(AAR 打包、
 * POM 生成、Gradle Module Metadata 里的 api/implementation 变体划分)有没有
 * 不小心丢掉聚合所需的东西。
 */
@HiltAndroidApp
class SmokeApplication : TelemetryApplication()
