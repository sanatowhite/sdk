package io.sanato.appkit.core.init

import android.app.Application
import javax.inject.Qualifier

/**
 * 具体采集器/预热逻辑实现这个接口,自己【不】调用 `registerActivityLifecycleCallbacks`
 * 或任何形式的自注册——执行顺序统一由 [AppInitializers] 驱动,保证启动顺序是
 * 一处可读的代码,而不是散落在各模块里的副作用。
 */
fun interface AppInitializer {
    fun init(application: Application)
}

/** `Application.onCreate()` 内同步执行,越少越好——真正重的逻辑放 [Deferred]。 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class Eager

/** 首帧绘制完成之后才执行(见 [FirstFrame] 里 `ViewTreeObserver.OnDrawListener` 的用法)。 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class Deferred
