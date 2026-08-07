# :core-init-hilt

## 这是什么 / 不是什么

`:core-init` 的 Hilt 装配:`AppInitializerModule` 的 `@Multibinds` 声明(`Set<AppInitializer>` 两组,保证空集合法)+ `HiltInitializingApplication` 基类。

**不是**:不含任何具体 `AppInitializer` 实现的绑定——那些绑定随各自的实现类所在模块走(比如遥测相关的四个 initializer 由 `:core-telemetry-hilt` 贡献)。

## 一行接入

```kotlin
dependencies {
    implementation("com.github.sanatowhite.sdk:core-init-hilt:1.0.0")
}
```

```kotlin
@HiltAndroidApp
class MyApp : HiltInitializingApplication() {
    override fun onPreDiSetup(application: Application) {
        // 需要早于 Hilt 组装完成的逻辑写在这里，而不是 attachBaseContext
    }
}
```

单独用这个模块时 `onPreDiSetup` 默认什么都不做——大多数消费方会直接依赖 `:core-telemetry-hilt` 的 `TelemetryApplication`(它是这个基类的子类,已经覆盖了 `onPreDiSetup` 去装崩溃 handler + 启动计时)。

## 公开 API

- `AppInitializerModule` — `@Module @InstallIn(SingletonComponent::class)`,只有两条 `@Multibinds` 声明,不含任何具体绑定。
- `HiltInitializingApplication` — 抽象 `Application` 基类:`attachBaseContext` 收窄成 `final`,子类覆盖 `onPreDiSetup(application)` 这个 hook 做早于 DI 的事;`onCreate()` 自动跑 Eager initializer + 首帧后跑 Deferred initializer。

## 已知限制 / 不要做的事

- **不要**覆盖 `attachBaseContext` 本身——它是 `final` 的,早于 Hilt 组装的逻辑一律写进 `onPreDiSetup`。
- 子类覆盖 `onPreDiSetup` 时如果调用了 `super.onPreDiSetup(application)`,要清楚父类默认实现是空的——真正有内容的是 `:core-telemetry-hilt` 的 `TelemetryApplication`,继承链是 `MyApp → TelemetryApplication → HiltInitializingApplication`,别漏了中间这一层。
