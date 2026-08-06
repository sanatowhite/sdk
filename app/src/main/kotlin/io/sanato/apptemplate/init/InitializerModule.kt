package io.sanato.apptemplate.init

import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.Multibinds

/**
 * 目前两个集合都是空的——Phase 6 加入 core-telemetry 的采集器之后才会有真正的
 * `@IntoSet` 绑定。`Set<AppInitializer>` 空集在 Hilt 里默认是编译错误,必须
 * 显式 `@Multibinds` 声明,否则"Firebase 关闭且无其他初始化任务"时直接编译失败。
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class InitializerModule {
    @Multibinds
    @Eager
    abstract fun eagerInitializers(): Set<AppInitializer>

    @Multibinds
    @Deferred
    abstract fun deferredInitializers(): Set<AppInitializer>
}
