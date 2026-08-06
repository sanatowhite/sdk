package io.sanato.appkit.core.init

import android.app.Application
import javax.inject.Inject

/**
 * 只用 `javax.inject.Inject` 构造器标注，不依赖 Hilt 运行时/插件（ADR 0004：
 * core-* 模块要 DI 友好但不绑死 Hilt）——不用 Hilt 的消费方可以直接
 * `AppInitializers(eagerInitializers = setOf(...), deferredInitializers = setOf(...))`
 * 手动构造。Hilt 消费方通过 `:core-init-hilt` 的 `@Multibinds` 声明 + 自己
 * 的 `@Binds @IntoSet` 绑定来注入这两个 Set。
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
