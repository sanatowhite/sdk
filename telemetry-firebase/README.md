# :telemetry-firebase

## 这是什么 / 不是什么

`:core-telemetry` 的 `Telemetry` 接口的 Firebase 实现(Analytics + Crashlytics),以及把它贡献进 `:app` Hilt 图的 `@Module`。**默认不参与构建**——`gradle.properties` 的 `telemetryFirebaseEnabled=false` 时,`settings.gradle.kts` 根本不会 `include` 这个模块,`:app` 也不会依赖它、不会 apply `google-services`/`firebase-crashlytics` 两个 Gradle 插件。这是零 Firebase 依赖,不是"依赖还在只是关闭"。

**不是**:不含 Firebase Performance Monitoring——`:core-telemetry` 已经自己采集了启动/帧/网络耗时,再叠一层 Firebase Perf 纯属重复。

## 如何开启

两步:
1. `gradle.properties` 把 `telemetryFirebaseEnabled` 改成 `true`。
2. 把你自己 Firebase 项目的 `google-services.json` 放进 `app/google-services.json`(**不要提交示例/占位 json**——`google-services` 插件会校验其中 `package_name` 与 `applicationId` 匹配,bootstrap 改名后假 json 必然导致构建失败)。

关闭同理:改回 `false`,`app/google-services.json` 留不留在磁盘上都不影响构建(模块和插件都不会被触发)。

## 独立引入

这个模块**不设计成独立复用**——它是 `:app` DI 图的一部分(贡献 `@IntoSet` 绑定到 `:app` 声明的 `Set<Telemetry>` multibinding),不像 `core-*` 模块那样通过复制目录使用。想要类似能力,参考 `FirebaseTelemetry.kt` 的实现自己接线。

## 公开 API

- `FirebaseTelemetry(analytics, crashlytics)` — 实现 `Telemetry`,固定 schema 方法都转换成 `FirebaseAnalytics.logEvent` + 对应 `Bundle`;`crash()`/`anr()` 走 `FirebaseCrashlytics.recordException`。
- `FirebaseTelemetryModule` — `@Module @InstallIn(SingletonComponent::class)`,`@Provides @IntoSet` 贡献一个 `Telemetry` 实例。**没有任何反射或运行时判断**——这个类是否存在于编译产物里,直接由 `telemetryFirebaseEnabled` 决定,Hilt 在 `:app` 聚合阶段要么发现它要么发现不了它,不存在"关闭时仍然打包了 Firebase 代码只是不跑"的情况。

## 已知限制 / 不要做的事

- **不要**给这两个 Gradle 插件用 `plugins { ... apply false }`——那样即使 `apply false`,Gradle 仍会解析下载插件 marker,达不到"关闭时零 Firebase 网络请求"。开关必须体现在根 `build.gradle.kts` 的 `buildscript { dependencies { ... } }` 是否有条件添加 classpath。
- **不要**提交任何形式的示例/占位 `google-services.json`——见上文,包名不匹配会让 fork 出去的人第一次构建就失败,且难以定位原因。
- **不要**在这个模块里加 productFlavor 或多环境判断——环境切换(测试/生产 Firebase 项目)属于 `google-services.json` 本身的职责(Firebase 支持一个项目挂多个 app 变体),不需要在这里重新发明。
