# :core-common-hilt

## 这是什么 / 不是什么

`:core-common` 的 Hilt 装配:三个 dispatcher qualifier(`@IoDispatcher`/`@DefaultDispatcher`/`@MainImmediateDispatcher`)的 provider,以及 `AppBuildInfo` 的零配置 provider(含可选覆盖)。

**不是**:不含任何具体能力实现——那些类型本身定义在 `:core-common`,这个模块只负责把它们接进 Hilt 图。

## 一行接入

```kotlin
dependencies {
    implementation("com.github.sanatowhite.sdk:core-common-hilt:1.0.0")
}
```

之后直接 `@Inject constructor(@IoDispatcher private val io: CoroutineDispatcher, private val buildInfo: AppBuildInfo)` 就能用,不用自己写 provider。

想要 `AppBuildInfo.gitSha`/`buildTimeMillis` 是真实值(自己 convention plugin 注进 `BuildConfig` 的那两项)?加 4 行:

```kotlin
@Module
@InstallIn(SingletonComponent::class)
object MyAppInfoOverrideModule {
    @Provides
    fun override() = AppBuildInfoOverride(BuildConfig.GIT_SHA, BuildConfig.BUILD_TIME_MILLIS)
}
```

不加就是零配置,这两个字段维持默认值(空字符串/0)。

## 公开 API

- `DispatchersModule` — `@Module`,提供 `@IoDispatcher`(`Dispatchers.IO`)/ `@DefaultDispatcher`(`Dispatchers.Default`)/ `@MainImmediateDispatcher`(`Dispatchers.Main.immediate`)三个绑定。
- `AppInfoModule` — `@Module`,提供 `AppBuildInfo`(`@Singleton`),默认从 `PackageManager` 读取,`gitSha`/`buildTimeMillis` 支持可选覆盖。
- `AppInfoOverrideModule` — `@BindsOptionalOf` 声明,配合 `AppBuildInfoOverride` 使用(见上方示例)。
- `AppBuildInfoOverride(gitSha, buildTimeMillis)` — 覆盖用的 data class。

## 已知限制 / 不要做的事

- 这三个 dispatcher qualifier 是我们自己的 FQN(`io.sanato.appkit.core.common.di.*`),和消费方自己已有的 dispatcher qualifier 不会撞——如果消费方之前手写过 provider 填这个坑,加这个模块后要迁移到用它,不要两边都保留(会造成 duplicate binding)。
