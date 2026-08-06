package io.sanato.appkit.telemetry.firebase

import android.app.Application
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.crashlytics.FirebaseCrashlytics
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import io.sanato.appkit.core.telemetry.Telemetry

/**
 * 只有当消费方实际依赖 `:telemetry-firebase` 时,Hilt 才会在聚合阶段发现这个
 * Module,贡献进 `:core-telemetry-hilt` 的 `TelemetryBackendsModule` 声明的
 * 同一个 `Set<Telemetry>`——不需要任何反射或运行时开关判断,依赖声明直接决定
 * 这个类是否存在于编译产物里。这个模块本身必须 apply Hilt Gradle 插件/KSP
 * 才能被正确聚合,不能只靠"class 在 classpath 上"(见
 * docs/adr/spike-0000-hilt-library-module-aggregation.md)。
 *
 * 这是 `:app` 模板默认的遥测后端——不再有 `telemetryFirebaseEnabled` 这类
 * 编译期开关,fork 者换掉 `app/google-services.json` 就能指向自己的项目。
 */
@Module
@InstallIn(SingletonComponent::class)
object FirebaseTelemetryModule {
    @Provides
    @IntoSet
    fun provideFirebaseTelemetry(application: Application): Telemetry =
        FirebaseTelemetry(
            analytics = FirebaseAnalytics.getInstance(application),
            crashlytics = FirebaseCrashlytics.getInstance(),
        )
}
