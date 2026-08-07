package io.sanato.appkit.core.init

import android.app.Application
import javax.inject.Inject

/**
 * 只用 `javax.inject.Inject` 构造器标注，不依赖 Hilt 运行时/插件（ADR 0004：
 * core-* 模块要 DI 友好但不绑死 Hilt）——不用 Hilt 的消费方可以直接
 * `AppInitializers(eagerInitializers = setOf(...), deferredInitializers = setOf(...))`
 * 手动构造。Hilt 消费方通过 `:core-init-hilt` 的 `@Multibinds` 声明 + 自己
 * 的 `@Binds @IntoSet` 绑定来注入这两个 Set。
 *
 * ⚠️ `Set<AppInitializer>` 内部的迭代顺序【没有契约保证】——Dagger 多绑定的
 * `Set` 实践中常是按 `@Binds` 声明顺序构建的 `LinkedHashSet`,但那是实现细节,
 * 重排 `@Binds` 方法可能静默改变执行顺序。目前没有任何初始化器依赖同组内的
 * 顺序,也别在不改成 `@IntoList` + 显式 index qualifier 的前提下,悄悄制造出
 * 这种依赖。
 */
class AppInitializers
    @Inject
    constructor(
        @Eager private val eagerInitializers: Set<@JvmSuppressWildcards AppInitializer>,
        @Deferred private val deferredInitializers: Set<@JvmSuppressWildcards AppInitializer>,
    ) {
        fun runEager(application: Application) {
            eagerInitializers.forEach { it.init(application) }
        }

        fun runDeferred(application: Application) {
            deferredInitializers.forEach { it.init(application) }
        }
    }
