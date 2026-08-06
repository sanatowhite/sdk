# :feature-feedback

## 这是什么 / 不是什么

反馈页——不自建后端,本地拼一封邮件(附带可选截图 + 最近日志)交给用户手机上已装的邮件客户端发送。Screen(无状态)/ Route(绑 `hiltViewModel()`)双层拆分。

**不是**:不含设置/关于/更新检查等其它标准页面(见 `:feature-settings`/`:feature-update`),不做真实的问题追踪系统对接。

## 一行接入

```kotlin
dependencies {
    implementation("com.github.sanatowhite.sdk:feature-feedback:1.0.0")
}
```

默认自带 Hilt 装配(`core-common-hilt` 提供 `AppBuildInfo`),不需要自己写任何 `@Module`。

## 挂进导航 + 截图捕获

```kotlin
NavHost(navController, startDestination = Home) {
    composable<Home> { ... }
    feedbackGraph(navController)
}
```

"附带截图"功能需要宿主用 `FeedbackScreenshotHost` 包住内容根节点(通常包在 `setContent` 最外层),否则不会崩,只是截不到图:

```kotlin
setContent {
    FeedbackScreenshotHost {
        AppNavHost(startDestination = Home)
    }
}
```

## 公开 API

- `FeedbackRoute(onNavigateBack, viewModel)` — Hilt 消费方入口,挂在 `feedbackGraph()` 里自动接好。
- `FeedbackScreen(description, includeScreenshot, includeLogs, onDescriptionChange, ..., onSend, onNavigateBack)` — 无状态版本,不用 Hilt 也能直接用。
- `feedbackGraph(navController)` — 挂进消费方 `NavHost` 的扩展函数。
- `FeedbackRoute`(路由类型)—— `@Serializable data object`。
- `FeedbackScreenshotHost(content)` — 包住宿主内容根节点,提供截图捕获点。

## FileProvider authority

反馈附件走 `${applicationId}.feedback.fileprovider`,和 `:updatechecker` 自己的 `${applicationId}.versioncheck.fileprovider` 是两个独立 authority——不要在消费方自己的 manifest 里再声明一个同名 provider,会在 manifest merge 时冲突。

## 已知限制 / 不要做的事

- 这个模块刻意不接入 `apiCheck`——理由同 `:core-ui`/`:feature-settings`。
- `FeedbackScreenshot.capture()` 只能捕获调用它那一刻屏幕上实际显示的内容(当前接线下就是反馈页本身)——已知限制,见源码注释。
- 想改邮件正文/设备信息格式?这两处目前不可配置,需要 fork 这个模块或提 issue,不是当前设计支持的扩展点。
