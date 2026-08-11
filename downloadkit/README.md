# :downloadkit

## 这是什么 / 不是什么

断点续传下载库：HTTP Range 续传、任务队列（并发上限 + 优先级）、磁盘持久化
（进程重启后可恢复）、可选前台服务 + 通知栏进度。入口是 `Downloader`（一个
按 `Context` 分进程单例的门面，形状照抄 `android.app.DownloadManager`/
`androidx.work.WorkManager` 的 `getInstance(context)` 惯例）。

**不是**：不是通用 HTTP 客户端（那是 `:core-net`），不做图片/视频解码或播放，
不做文件校验以外的内容处理，不管理下载完成后的文件生命周期（下载到哪、什么
时候删，由消费方通过 `DownloadRequest.destDir` 和自己的业务逻辑决定）。

## 独立引入

```kotlin
implementation("com.github.sanatowhite.sdk:downloadkit:<version>")
```

零 Hilt。`Downloader.getInstance(context)` 一行拿到可用实例，不需要任何 DI
框架。想要 Hilt 一键接线（`@Inject constructor(downloader: Downloader)`）,
额外引入 `:downloadkit-hilt`（见其 README）。

## 为什么这个模块依赖 :core-net（不是零依赖自包含）

本仓其余 `*kit` 模块（`:updatechecker`、`:backupkit`、`:logkit`）都是零内部
依赖、纯 `HttpURLConnection`/`android.*` 的自包含库。`:downloadkit` 是第一个
例外——`docs/adr/0013-downloadkit-depends-on-core-net.md` 记录了完整取舍，
核心理由：Range 续传需要流式响应体 + 连接池，用 `HttpURLConnection` 手写会
重新发明 OkHttp 已经踩过的所有坑（连接复用、TLS 会话缓存、chunked 传输）。
代价是发布 POM 会带上 okhttp + retrofit + kotlinx-serialization 三条传递链
——这是 ADR 0013 里权衡后接受的成本，不是疏漏。

## AI 接入指南（可直接执行）

### 最小可用：手动构造，零 DI

```kotlin
val downloader = Downloader.getInstance(context)  // context 会被转成 applicationContext
val id = downloader.enqueue(
    DownloadRequest(url = "https://example.com/file.zip", fileName = "file.zip"),
)
downloader.observe(id).collect { state -> /* DownloadState.Queued/Running/Paused/Completed/Failed/Canceled */ }
```

`enqueue()` 用 `url` + `fileName` 派生一个**确定性** id（`taskIdFor`，SHA-256
截断）——同一对 `url`/`fileName` 再次 `enqueue()`（比如进程重启后）是幂等的：
直接续接已有任务的进度，不会开一个重复任务。这是"断点续传"在 API 层面的
真正含义，不只是"Range 头写对了"。

### 自定义并发上限 / 重试策略 / 通知开关

```kotlin
val config = DownloadConfig(
    downloadDir = File(context.filesDir, "downloads"),
    maxConcurrent = 3,               // 默认 3
    retryPolicy = DownloadRetryPolicy(maxAttempts = 5),   // 指数退避 + 抖动
    notificationsEnabled = true,      // false = 只在进程内跑队列，不拉前台服务/不发通知
)
val downloader = Downloader.getInstance(context, config = config)
```

⚠️ `getInstance()` 是"第一个调用者说了算"——`config`/`client`/`notifier`
只在进程首次调用时生效。想要自定义配置，在 `Application.onCreate()` 里尽早
调一次；晚于其他调用方的 `getInstance()` 调用不会改变已经创建的实例。

### 暂停 / 恢复 / 取消

```kotlin
downloader.pause(id)      // 取消正在跑的传输，保留 .part 文件，状态 → Paused
downloader.resume(id)     // Paused/Failed → 重新排队
downloader.cancel(id)     // 删除 .part/.meta，状态 → Canceled（不可恢复）
downloader.cancelAll()
```

### 不要做的事

- **不要**自己拼 `Range`/`If-Range` 头调用 `OkHttpDownloadEngine`——那是
  `internal` 实现细节，公开入口只有 `Downloader`。
- **不要**把 `HttpClientFactory.okHttpClient()` 的返回值直接传给
  `Downloader.getInstance(client = ...)`——它的 `callTimeout(30s)` 会杀掉任何
  超过 30 秒的下载。要传自定义 `OkHttpClient`，先过一遍
  `Downloader.downloadOkHttpClient(base)`（同 `:core-net` `WebSocketFactory`
  的派生 client 模式）。
- **不要**假设 `DownloadState.Failed` 是终态——`.part` 文件还在，`resume()`
  可以从断点继续；真正不可恢复的终态只有 `Completed`/`Canceled`。

## 公开 API

- `Downloader` — 门面。`tasks: StateFlow<List<DownloadTask>>`、
  `enqueue`/`pause`/`resume`/`cancel`/`cancelAll`/`observe`、
  `hasNotificationPermission()`、`companion.getInstance()`/`downloadOkHttpClient()`。
- `DownloadRequest` — `url`/`fileName`/`destDir`/`sha256`/`headers`/`allowMetered`/`priority`。
- `DownloadTask` / `DownloadState`（`Queued`/`Running`/`Paused`/`Completed`/`Failed`/`Canceled`，sealed）。
- `DownloadConfig` / `DownloadRetryPolicy`。
- `DownloadError`（sealed，`Http`/`Network`/`Io`/`UnexpectedResponse`/`ChecksumMismatch`/`Canceled`/`MaxRetriesExceeded`/`Unknown`）。
- `notify.DownloadNotifier` — 通知栏渲染接口，`notify.AndroidDownloadNotifier` 是默认实现，均可替换。

## 已知限制 / 不要做的事

- 前台服务在 API 31+ 若 App 处于后台会被系统拒绝启动（`ensureStarted` 已捕获
  并吞掉这个异常）——下载不受影响，只是没有可见通知，直到下次进前台。
- API 35+ 的 `dataSync` 前台服务类型有约 6 小时执行上限；到点后
  `DownloadService.onTimeout` 会自动暂停所有任务并优雅退出前台，不会崩溃。
- 通知栏是**一条汇总通知**（"下载中 2/5 · 43%"），不是每任务一条——见
  `notify/DownloadService.kt` 的 KDoc。
- 断点续传依赖服务端支持 `Range`/`If-Range`；服务端不支持时会整段重下（有先
  例保护：不会因为不支持续传就报错，只是效率退化）。
