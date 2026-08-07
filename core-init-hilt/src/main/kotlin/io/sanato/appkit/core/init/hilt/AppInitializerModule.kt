package io.sanato.appkit.core.init.hilt

import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.Multibinds
import io.sanato.appkit.core.init.AppInitializer
import io.sanato.appkit.core.init.Deferred
import io.sanato.appkit.core.init.Eager

/**
 * `@Multibinds` 声明本身不依赖是否存在具体绑定——即使消费方在别的 `@Module`
 * 里贡献了真正的 `@Binds @IntoSet`（比如 `:core-telemetry-hilt` 或消费方自己的
 * app 模块），保留这两行仍然安全:哪天所有具体绑定都被移除,Hilt 也会安全地
 * 退化成空集而不是编译报错。
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class AppInitializerModule {
    @Multibinds
    @Eager
    abstract fun eagerInitializers(): Set<AppInitializer>

    @Multibinds
    @Deferred
    abstract fun deferredInitializers(): Set<AppInitializer>
}
