# :core-data

## 这是什么 / 不是什么

本地持久化的用户设置层:`UserSettings`(主题模式/动态取色/通知/遥测开关/同意版本号)+ DataStore Preferences 封装(`UserSettingsRepository` 接口 + `DataStoreUserSettingsRepository` 实现)。

**不是**:不含网络请求,不依赖 `:core-net`。不含语言设置——应用内语言切换由 `AppCompatDelegate` 托管,不进 DataStore。不含 Room/SQL——模板刻意不内置数据库,`TEMPLATE.md` 给"15 分钟加 Room"的配方。不含 Hilt 绑定——那是 `:core-data-hilt` 的职责,这个模块只暴露接口。

## 一行接入

```kotlin
dependencies {
    implementation("com.github.sanatowhite.sdk:core-data:1.0.0")
    // 想要开箱即用的 DataStore 实现绑定(Hilt),再加：
    implementation("com.github.sanatowhite.sdk:core-data-hilt:1.0.0")
}
```

不需要改我们的任何源码。传递依赖:`androidx.datastore:datastore-preferences`(`api()` 传给消费方)。

想换掉 DataStore、用自己的存储实现?排掉 `core-data-hilt` 这个绑定模块就行:

```kotlin
implementation("com.github.sanatowhite.sdk:core-data-hilt:1.0.0") {
    exclude(group = "com.github.sanatowhite.sdk", module = "core-data-hilt")
}
```

然后自己写一条 `@Binds MyRepo : UserSettingsRepository`——`:core-data` 本身只依赖接口,不绑死任何实现。

## 公开 API

- `UserSettings` — data class:`themeMode`(`ThemeMode` 枚举:SYSTEM/LIGHT/DARK)、`dynamicColorEnabled`、`notificationsEnabled`、`telemetryEnabled`、`consentVersion`。
- `UserSettingsRepository` — 接口:`val settings: Flow<UserSettings>` + 五个 `setXxx` 挂起函数。
- `DataStoreUserSettingsRepository(dataStore: DataStore<Preferences>)` — 真实实现,`@Inject` 构造函数,由 `:core-data-hilt` 提供 `DataStore<Preferences>`(用本模块的 `Context.userSettingsDataStore` 扩展属性)。
- `Context.userSettingsDataStore: DataStore<Preferences>` — 顶层委托属性,保证每个进程只有一个实例。
- **testFixtures**:`FakeUserSettingsRepository` — 纯内存实现,`MutableStateFlow` 驱动,不落盘,给 ViewModel 单测用。依赖方式:`testImplementation(testFixtures(project(":core-data")))`(仓库内可用;发布出去的坐标不带 test-fixtures artifact)。

## 已知限制 / 不要做的事

- **不要**把语言设置塞进 `UserSettings`——`AppCompatDelegate.setApplicationLocales` 自己管理持久化(存在系统的 `AppLocalesMetadataHolderService` 里),重复存一份到 DataStore 只会导致两个真源不一致。
- **不要**把 `Context.userSettingsDataStore` 声明成非顶层的普通函数——`preferencesDataStore` 委托要求是顶层 `val` 才能保证进程内单例;如果拿去包成函数每次调用都新建,会触发 DataStore 的"同一文件多个实例"运行时异常。
- 读取失败(如枚举字符串损坏)一律静默回退默认值(见 `parseThemeMode` 的 `runCatching`),不抛异常糊到 UI 层——用户设置这一层的可用性优先于"暴露损坏数据"。
- `UserSettings.telemetryEnabled` 目前是个死开关——没有任何代码真正读它去关闭 `:core-telemetry` 的采集,这是已知的合规缺口,不在这次改造范围内。
