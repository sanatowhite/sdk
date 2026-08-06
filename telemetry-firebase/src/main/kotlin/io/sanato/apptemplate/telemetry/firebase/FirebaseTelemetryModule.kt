package io.sanato.apptemplate.telemetry.firebase

import android.app.Application
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.crashlytics.FirebaseCrashlytics
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import io.sanato.apptemplate.core.telemetry.Telemetry

/**
 * 只有当 `:app` 实际 `implementation(project(":telemetry-firebase"))` 时
 * (即 `telemetryFirebaseEnabled=true`),Hilt 才会在聚合阶段发现这个 Module,
 * 贡献进 `:app` `TelemetryBackendsModule` 声明的同一个 `Set<Telemetry>`——
 * 不需要任何反射或运行时开关判断,开关状态直接决定这个类是否存在于编译产物里。
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
