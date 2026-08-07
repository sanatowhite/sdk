# :feature-update

## 这是什么 / 不是什么

`:updatechecker` 的 Compose/Hilt 粘合层——检查更新对话框(下载进度/强制更新/校验失败等状态)+ `UpdateCheckHost` 一行接入的状态持有。`:feature-update` 和 `:updatechecker` 现在同属一个发布集,用 `project()` 依赖,不需要额外坐标替换。

**不是**:不做真正的下载/校验/安装逻辑(那些在 `:updatechecker`,零 Compose/Hilt 依赖),这个模块只是粘合。

## 一行接入

```kotlin
dependencies {
    implementation("com.github.sanatowhite.sdk:feature-update:1.0.0")
}
```

默认指向一个占位更新配置地址(`https://raw.githubusercontent.com/OWNER/REPO/main/update.json`)——`checkForUpdate()` 不会崩,只会稳定拿到 `UpdateResult.Error`,和 fork 出去当天的行为完全一致。真正接入前,提供一个可选 Hilt 绑定换成自己的地址:

```kotlin
@Module
@InstallIn(SingletonComponent::class)
object MyUpdateConfigOverrideModule {
    @Provides
    fun override() = UpdateConfigOverride("https://raw.githubusercontent.com/OWNER/REPO/main/update.json")
}
```

JSON schema 见 `:updatechecker` 的 `UpdateConfigParser`/`UpdateInfo`。

## AI 接入指南(可直接执行)

**要不要用这个模块**:想要"检查更新"用 Compose 弹窗 + 一行状态持有(而不是自己拼 `:updatechecker` 的原生 `AlertDialog` API)时用。

**接入步骤**:
1. 加坐标(见上方,会传递依赖 `:updatechecker`)。
2. `UpdateCheckHost { onCheckForUpdates -> ... }` 包住 `setContent {}` 里的内容(见下方代码块),把 `onCheckForUpdates` 传给 `:feature-settings` 的 `settingsGraph(onCheckForUpdates = ...)`(如果同时用了 `:feature-settings`),或者自己接一个按钮调用它。
3. **必须**提供 `UpdateConfigOverride` 才能真正检查到更新——照抄本文件上方的 `MyUpdateConfigOverrideModule` 代码块,把占位 URL 换成自己的更新配置 JSON 地址。不提供也不会崩,只是 `checkForUpdate()` 永远拿到 `Error`。

**验证**:`./gradlew :app:assembleDebug` 编译通过(即使没提供 `UpdateConfigOverride` 也能过);提供真实配置地址后,把远程 JSON 里的 `versionCode` 改成比本地大的值,触发一次检查,确认弹出更新对话框。

**不要做的事**:见下面"已知限制"——不要以为"检查更新返回 Error"就是坏了,先确认是不是忘了提供 `UpdateConfigOverride`。

## 挂进你的 Compose 树

```kotlin
setContent {
    UpdateCheckHost { onCheckForUpdates ->
        NavHost(navController, startDestination = Home) {
            composable<Home> { ... }
            settingsGraph(navController, onCheckForUpdates = onCheckForUpdates)   // 引了 :feature-settings 才传
        }
    }
}
```

`UpdateCheckHost` 持有跨屏幕的对话框状态、展示 `UpdateDialog`、UpToDate/Error 时弹 Toast——一行包住你自己的 `NavHost`;`onCheckForUpdates` 回调不知道也不需要知道谁触发它,不用 `:feature-settings` 也能自己接一个按钮调用。

## 公开 API

- `UpdateCheckHost(viewModel, content)` — 一行接入的状态持有 + 弹窗 + Toast。
- `UpdateViewModel` / `UpdateUiState` — 想自己接线(比如换 Toast 为 Snackbar)就直接用这两个 + `UpdateDialog`,跳过 `UpdateCheckHost`。
- `UpdateDialog(state, onDownload, onInstall, onDismiss)` — 无状态,不用 Hilt 也能直接用。
- `UpdateConfig` / `UpdateConfigOverride` — 更新配置地址的注入点,见上面"一行接入"。

## 已知限制 / 不要做的事

- 这个模块刻意不接入 `apiCheck`——理由同 `:core-ui`/`:feature-settings`/`:feature-feedback`。
- 不提供 `UpdateConfigOverride` 就一直停在占位地址上——这是刻意的"开箱即用但不做真事"默认值,不是 bug。
