package io.sanato.appkit.core.init.hilt

import android.app.Application
import android.content.Context
import io.sanato.appkit.core.init.AppInitializers
import io.sanato.appkit.core.init.FirstFrame
import javax.inject.Inject

/**
 * 消费方 Hilt Application 的基类：`Application.onCreate()` 里跑 Eager
 * initializer、首帧后跑 Deferred initializer，全部交给注入进来的
 * [AppInitializers]（其 `Set<AppInitializer>` 由 [AppInitializerModule] 的
 * `@Multibinds` + 具体模块的 `@Binds @IntoSet` 共同贡献）。
 *
 * `attachBaseContext` 收窄成 `final`：真正需要在 Hilt 组装之前运行的逻辑
 * （比如崩溃 handler 安装、启动计时——这两件事必须早于 DI，是编排机制覆盖
 * 不到的例外）覆盖 [onPreDiSetup] 这个 hook，不要覆盖 `attachBaseContext`
 * 本身。`:core-telemetry-hilt` 的 `TelemetryApplication` 就是这么做的。
 */
abstract class HiltInitializingApplication : Application() {
    @Inject
    lateinit var appInitializers: AppInitializers

    /** 早于 Hilt 组装执行——默认什么都不做。 */
    protected open fun onPreDiSetup(application: Application) = Unit

    final override fun attachBaseContext(base: Context) {
        super.attachBaseContext(base)
        onPreDiSetup(this)
    }

    override fun onCreate() {
        super.onCreate()
        appInitializers.runEager(this)
        FirstFrame.onFirstDraw(this) { appInitializers.runDeferred(this) }
    }
}
