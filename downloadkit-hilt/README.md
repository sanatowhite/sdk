# :downloadkit-hilt

## 这是什么 / 不是什么

`:downloadkit`（`Downloader`）的默认 Hilt 接线——一个文件
（`DownloadModule.kt`），提供 `@Singleton Downloader`，并暴露三个
`@BindsOptionalOf` 覆盖点。和 `:core-auth`/`:auth-firebase` 的关系不同，这个
伴生模块不是"唯一实现"意义上必需的——`Downloader.getInstance(context)` 本身
就是可用的，不用 Hilt 也能拿到同一个进程级单例；这个模块纯粹是为想要
`@Inject constructor(downloader: Downloader)` 而不想手写
`Downloader.getInstance(...)` 调用点的消费方准备的。

**不是**：不提供任何 `Downloader` 之外的绑定，不重新实现 `:downloadkit` 的
任何逻辑。

## 独立引入

```kotlin
implementation("com.github.sanatowhite.sdk:downloadkit-hilt:<version>")
```

同时传递引入 `:downloadkit`。消费方的 `:app` 模块需要自己 apply
`com.google.dagger.hilt.android` 插件（Hilt Gradle 插件的 `verifyDependencies`
只看直接 apply 插件的那个模块，不看 `-hilt` 伴生模块内部各自声明的
`hilt-android`——同 `:auth-net-hilt` README 的说明）。

## AI 接入指南（可直接执行）

```kotlin
@HiltViewModel
class DownloadViewModel @Inject constructor(
    private val downloader: Downloader,
) : ViewModel() {
    val tasks: StateFlow<List<DownloadTask>> = downloader.tasks
    fun enqueue(url: String, fileName: String) = downloader.enqueue(DownloadRequest(url, fileName))
}
```

### 覆盖默认配置 / 通知实现

```kotlin
@Module
@InstallIn(SingletonComponent::class)
object MyDownloadConfigModule {
    @Provides
    fun provideDownloadConfigOverride(@ApplicationContext context: Context): DownloadConfigOverride =
        DownloadConfigOverride(DownloadConfig(downloadDir = File(context.filesDir, "my_downloads"), maxConcurrent = 5))
}
```

同样的 `@BindsOptionalOf` 套路可以覆盖 `notify.DownloadNotifier`（换掉默认
通知栏样式）。不提供覆盖绑定时，`DownloadModule` 落到
`DownloadConfig.default(context)` / `AndroidDownloadNotifier` 这两个占位默认
值——这是刻意的默认，不是 bug（同 `:feature-update` `UpdateConfigModule` 的
占位 URL 设计）。

### 网络遥测

如果同时引入 `:net-telemetry-hilt`，`Downloader` 内部的 `OkHttpClient` 会自动
带上 `NetworkMetricsSink`（`@BindsOptionalOf` 落空即为 no-op，不需要额外接线）。

## 公开 API

- `DownloadConfigOverride` — 覆盖默认 `DownloadConfig` 的绑定载体。
- `DownloadBindsModule` — 三个 `@BindsOptionalOf` 钩子（`NetworkMetricsSink`/`DownloadConfigOverride`/`DownloadNotifier`）。
- `DownloadModule` — 提供 `@Singleton Downloader`。

## 已知限制 / 不要做的事

- **不要**在这个模块里新建第二个 `Downloader` 相关的绑定——`Downloader`
  本身已经是进程级单例（见 `:downloadkit` README），这里只做"接进 Hilt 图"
  这一件事。
- 没有独立测试文件——纯 `@Provides`/`@BindsOptionalOf` 声明式绑定代码，同
  `:net-telemetry-hilt` 的先例，实际行为由 `:downloadkit` 自己的测试覆盖，
  Hilt 聚合本身由 `checks/consumer-smoke` 兜底。
