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

## AI 接入指南(可直接执行)

**要不要用这个模块**:大多数情况下**不需要直接依赖它**——如果已经在用 `:core-telemetry-hilt`,它的 `TelemetryApplication` 已经传递依赖了这个模块,直接继承 `TelemetryApplication` 而不是这个模块的 `HiltInitializingApplication`。只有"要 `:core-init` 的启动编排但不要 `:core-telemetry` 的采集器"这种场景才直接依赖这个模块。

**接入步骤**:
1. 加坐标:`implementation("com.github.sanatowhite.sdk:core-init-hilt:1.0.0")`。
2. 自己的 `Application` 继承 `HiltInitializingApplication`,加 `@HiltAndroidApp`;需要早于 Hilt 组装的逻辑覆盖 `onPreDiSetup`,**不要覆盖 `attachBaseContext`**(它是 `final` 的)。
3. 具体的 `AppInitializer` 实现按 Eager/Deferred 用 `@Binds @IntoSet` 绑进对应的 `Set<AppInitializer>`。

**验证**:启动 app,确认 `Eager` initializer 在 `Application.onCreate()` 期间就跑完,`Deferred` initializer 在首帧画完之后才跑——加一行 log 打印时间戳即可肉眼确认。

**不要做的事**:见"已知限制"——不要覆盖 `attachBaseContext`。

## 公开 API

- `AppInitializerModule` — `@Module @InstallIn(SingletonComponent::class)`,只有两条 `@Multibinds` 声明,不含任何具体绑定。
- `HiltInitializingApplication` — 抽象 `Application` 基类:`attachBaseContext` 收窄成 `final`,子类覆盖 `onPreDiSetup(application)` 这个 hook 做早于 DI 的事;`onCreate()` 自动跑 Eager initializer + 首帧后跑 Deferred initializer。

## 已知限制 / 不要做的事

- **不要**覆盖 `attachBaseContext` 本身——它是 `final` 的,早于 Hilt 组装的逻辑一律写进 `onPreDiSetup`。
- 子类覆盖 `onPreDiSetup` 时如果调用了 `super.onPreDiSetup(application)`,要清楚父类默认实现是空的——真正有内容的是 `:core-telemetry-hilt` 的 `TelemetryApplication`,继承链是 `MyApp → TelemetryApplication → HiltInitializingApplication`,别漏了中间这一层。
