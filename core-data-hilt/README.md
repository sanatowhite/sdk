# :core-data-hilt

## 这是什么 / 不是什么

`:core-data` 的 Hilt 装配:提供 `DataStore<Preferences>` + 把 `DataStoreUserSettingsRepository` 绑定到 `UserSettingsRepository`。

**不是**:不含任何存储实现本身——那些在 `:core-data`,这个模块只负责默认接线。

## 一行接入

```kotlin
dependencies {
    implementation("com.github.sanatowhite.sdk:core-data-hilt:1.0.0")
}
```

之后直接 `@Inject constructor(private val settingsRepo: UserSettingsRepository)` 就能用,零配置——默认存储是 DataStore Preferences。

## AI 接入指南(可直接执行)

**要不要用这个模块**:用了 `:core-data` 且用 Hilt、想要开箱即用的 DataStore 实现时加;想换成自己的存储实现(Room、自定义文件等)就不要加,直接依赖 `:core-data` 接口自己写 `@Binds`。

**接入步骤**:
1. 加坐标:`implementation("com.github.sanatowhite.sdk:core-data-hilt:1.0.0")`(需要同时依赖 `:core-data`,通常 `:feature-settings` 已经传递引入了两者)。
2. 任何 `@Inject constructor` 直接声明 `val settingsRepo: UserSettingsRepository` 参数即可读写用户设置。

**验证**:`./gradlew :app:hiltJavaCompileDebug` 编译通过即代表绑定聚合正确;运行时写一次设置(比如切换主题)、重启 app,确认设置被持久化(读到的还是切换后的值)。

**不要做的事**:见"已知限制"——不要同时保留这个模块的绑定又自己再写一条 `@Binds ... : UserSettingsRepository`。

## 公开 API

- `DataStoreModule` — `@Module`,提供 `DataStore<Preferences>`(`@Singleton`,基于 `Context.userSettingsDataStore`)。
- `RepositoryModule` — `@Module`,`@Binds` 把 `DataStoreUserSettingsRepository` 绑定为 `UserSettingsRepository`。

## 已知限制 / 不要做的事

- 想换掉 DataStore、用自己的存储?排掉这个模块(`exclude(group = "com.github.sanatowhite.sdk", module = "core-data-hilt")`),自己写一条 `@Binds MyRepo : UserSettingsRepository`——`:core-data` 本身只依赖接口,换实现不需要碰它。
- **不要**同时保留这个模块的绑定又自己再写一条 `@Binds ... : UserSettingsRepository`——Hilt 会报 duplicate binding,两者二选一。
