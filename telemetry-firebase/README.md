# :telemetry-firebase

## 这是什么 / 不是什么

`:core-telemetry` 的 `Telemetry` 接口的 Firebase 实现(Analytics + Crashlytics),以及把它贡献进消费方 Hilt 图的 `@Module`。**这是 `:app` 模板默认的遥测后端**——`:app` 无条件依赖它、无条件 apply `google-services`/`firebase-crashlytics` 两个 Gradle 插件,仓库里提交了一份指向不存在项目的占位 `app/google-services.json`,让 `:app:assembleDebug` 开箱可编译/可运行。fork 者只需要把这个占位文件换成自己 Firebase 项目的真实 `google-services.json`,不需要改任何 Gradle/Kotlin 代码。

想要"零 Firebase"的消费方(不管是 fork 这个模板,还是只引用发布出去的 SDK 模块)只依赖 `:core-telemetry`(或 `:core-telemetry-hilt`),不引用这个模块即可——`Set<Telemetry>` 的 `@Multibinds` 保证空集是合法状态,不依赖这个模块的编译产物里完全不会出现 Firebase 相关代码。

**不是**:不含 Firebase Performance Monitoring——`:core-telemetry` 已经自己采集了启动/帧/网络耗时,再叠一层 Firebase Perf 纯属重复。

## 如何换成自己的 Firebase 项目

去 <https://console.firebase.google.com> 建一个项目,下载它的 `google-services.json`,覆盖 `app/google-services.json`。就这一步——包名(`applicationId`/三个 buildType 的 suffix)必须和你的 Firebase 项目里注册的 Android 应用包名一致,这是 `google-services` 插件的硬性校验,不是这个模块自己的逻辑。

## 如果想完全不要 Firebase

从 `app/build.gradle.kts` 里删掉:
1. `plugins {}` 块里的 `alias(libs.plugins.google.services)` / `alias(libs.plugins.firebase.crashlytics)`
2. `dependencies {}` 里的 `implementation(project(":telemetry-firebase"))`

以及根 `build.gradle.kts` `plugins {}` 块里对应的 `apply false` 两行(如果没有别的模块还在用)。删完之后 `app/google-services.json` 和 Firebase 依赖树都不会被触碰。

## 独立引入

依赖坐标 `com.github.sanatowhite.sdk:telemetry-firebase:<version>`,和其余 `-hilt` 模块一样是纯 Hilt 装配模块——只提供 `FirebaseTelemetryModule` 这一个 `@Module`,没有别的可调用 API。**必须 apply Hilt Gradle 插件才能被正确聚合**(见 `docs/adr/spike-0000-hilt-library-module-aggregation.md`):库模块的 `@Module` 若只是"在 classpath 上"而不自己跑 `hilt-compiler`,聚合阶段会静默找不到它,不报任何编译错误。

## 公开 API

- `FirebaseTelemetry(analytics, crashlytics)` — 实现 `Telemetry`,固定 schema 方法都转换成 `FirebaseAnalytics.logEvent` + 对应 `Bundle`;`crash()`/`anr()` 走 `FirebaseCrashlytics.recordException`。
- `FirebaseTelemetryModule` — `@Module @InstallIn(SingletonComponent::class)`,`@Provides @IntoSet` 贡献一个 `Telemetry` 实例进 `Set<Telemetry>` multibinding。

## 已知限制 / 不要做的事

- **不要**在这个模块里加 productFlavor 或多环境判断——环境切换(测试/生产 Firebase 项目)属于 `google-services.json` 本身的职责(Firebase 支持一个项目挂多个 app 变体),不需要在这里重新发明。
- 占位 `google-services.json` 里的 `project_id`/`api_key` 都是假的——编译/运行不受影响,但任何真实上报(Analytics 事件、Crashlytics 崩溃)都会静默失败,不会抛异常也不会有本地日志提示,这是 Firebase SDK 自身的行为,不是这个模块能改变的。
