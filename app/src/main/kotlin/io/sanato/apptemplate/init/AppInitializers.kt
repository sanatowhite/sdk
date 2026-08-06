package io.sanato.apptemplate.init

import android.app.Application
import javax.inject.Inject

/**
 * ⚠️ `Set<AppInitializer>` 内部的迭代顺序【没有契约保证】——Dagger 多绑定的
 * `Set` 实践中常是按 `@Binds` 声明顺序构建的 `LinkedHashSet`,但那是实现细节,
 * 重排 `InitializerModule.kt` 里的 `@Binds` 方法可能静默改变执行顺序。目前
 * 没有任何初始化器依赖同组内的顺序,也别在不改成 `@IntoList` + 显式 index
 * qualifier 的前提下,悄悄制造出这种依赖。
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
