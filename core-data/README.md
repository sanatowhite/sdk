# :core-data

## 这是什么 / 不是什么

本地持久化的用户设置层:`UserSettings`(主题模式/动态取色/通知/遥测开关/同意版本号)+ DataStore Preferences 封装(`UserSettingsRepository` 接口 + `DataStoreUserSettingsRepository` 实现)。

**不是**:不含网络请求(依赖 `:core-net` 只是为了共享 `AppResult`/`AppError` 这类通用类型,不发起任何请求)。不含语言设置——应用内语言切换由 `AppCompatDelegate` 托管,不进 DataStore(Phase 7 会说明原因)。不含 Room/SQL——模板刻意不内置数据库,`TEMPLATE.md`(Phase 11)会给"15 分钟加 Room"的配方。

## 独立引入

```kotlin
dependencies {
    implementation(project(":core-data")) // 会连带 project(":core-common") + project(":core-net")
}
```

传递依赖:`androidx.datastore:datastore-preferences`。如果你不需要 `:core-net` 的网络能力,只想要这一份设置存储,把 `implementation(project(":core-net"))` 那一行去掉不会导致编译错误(本模块目前没有真正用到 `core-net` 的任何类型,这条依赖是为将来把网络配置类设置——比如 API base URL 覆盖——并入这里预留的;如果你确定用不到,删掉这行依赖即可)。

## 公开 API

- `UserSettings` — data class:`themeMode`(`ThemeMode` 枚举:SYSTEM/LIGHT/DARK)、`dynamicColorEnabled`、`notificationsEnabled`、`telemetryEnabled`、`consentVersion`。
- `UserSettingsRepository` — 接口:`val settings: Flow<UserSettings>` + 五个 `setXxx` 挂起函数。
- `DataStoreUserSettingsRepository(dataStore: DataStore<Preferences>)` — 真实实现,`@Inject` 构造函数,由 `:app` 的 Hilt Module 提供 `DataStore<Preferences>`(用本模块的 `Context.userSettingsDataStore` 扩展属性)。
- `Context.userSettingsDataStore: DataStore<Preferences>` — 顶层委托属性,保证每个进程只有一个实例。
- **testFixtures**:`FakeUserSettingsRepository` — 纯内存实现,`MutableStateFlow` 驱动,不落盘,给 ViewModel 单测用。依赖方式:`testImplementation(testFixtures(project(":core-data")))`。

## 已知限制 / 不要做的事

- **不要**把语言设置塞进 `UserSettings`——`AppCompatDelegate.setApplicationLocales` 自己管理持久化(存在系统的 `AppLocalesMetadataHolderService` 里),重复存一份到 DataStore 只会导致两个真源不一致。
- **不要**把 `Context.userSettingsDataStore` 声明成非顶层的普通函数——`preferencesDataStore` 委托要求是顶层 `val` 才能保证进程内单例;如果拿去包成函数每次调用都新建,会触发 DataStore 的"同一文件多个实例"运行时异常。
- 读取失败(如枚举字符串损坏)一律静默回退默认值(见 `parseThemeMode` 的 `runCatching`),不抛异常糊到 UI 层——用户设置这一层的可用性优先于"暴露损坏数据"。
