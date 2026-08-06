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

## 公开 API

- `DataStoreModule` — `@Module`,提供 `DataStore<Preferences>`(`@Singleton`,基于 `Context.userSettingsDataStore`)。
- `RepositoryModule` — `@Module`,`@Binds` 把 `DataStoreUserSettingsRepository` 绑定为 `UserSettingsRepository`。

## 已知限制 / 不要做的事

- 想换掉 DataStore、用自己的存储?排掉这个模块(`exclude(group = "com.github.sanatowhite.sdk", module = "core-data-hilt")`),自己写一条 `@Binds MyRepo : UserSettingsRepository`——`:core-data` 本身只依赖接口,换实现不需要碰它。
- **不要**同时保留这个模块的绑定又自己再写一条 `@Binds ... : UserSettingsRepository`——Hilt 会报 duplicate binding,两者二选一。
